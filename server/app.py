import argparse
import contextlib
import getpass
import hashlib
import os
from pathlib import Path
import secrets
import sqlite3
import time
from urllib.parse import unquote
import uuid

from fastapi import FastAPI, Depends, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, ConfigDict, Field

DATA = Path(os.environ.get('PROMATH_DATA', './data')).resolve()
MAX_UPLOAD = 512 * 1024 * 1024

@contextlib.contextmanager
def db():
    DATA.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DATA / 'promath.sqlite', timeout=30)
    con.row_factory = sqlite3.Row
    con.execute('PRAGMA foreign_keys=ON')
    try:
        with con:
            yield con
    finally:
        con.close()

def initialize():
    (DATA / 'uploads').mkdir(parents=True, exist_ok=True)
    with db() as con:
        con.execute('PRAGMA journal_mode=WAL')
        con.executescript('''
        CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, username TEXT UNIQUE NOT NULL, name TEXT NOT NULL, salt TEXT NOT NULL, password_hash TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('student','instructor')));
        CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), expires REAL NOT NULL);
        CREATE TABLE IF NOT EXISTS courses(id TEXT PRIMARY KEY, owner_id TEXT NOT NULL REFERENCES users(id), title TEXT NOT NULL, description TEXT NOT NULL, published INTEGER NOT NULL DEFAULT 0);
        CREATE TABLE IF NOT EXISTS lessons(id TEXT PRIMARY KEY, course_id TEXT NOT NULL REFERENCES courses(id), title TEXT NOT NULL, stage INTEGER NOT NULL, position INTEGER NOT NULL, published INTEGER NOT NULL DEFAULT 0, asset_id TEXT);
        CREATE TABLE IF NOT EXISTS assets(id TEXT PRIMARY KEY, lesson_id TEXT NOT NULL REFERENCES lessons(id), filename TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, storage_name TEXT NOT NULL UNIQUE);
        CREATE TABLE IF NOT EXISTS attempts(bucket TEXT NOT NULL, at REAL NOT NULL);
        CREATE INDEX IF NOT EXISTS attempts_bucket ON attempts(bucket,at);
        ''')

@contextlib.asynccontextmanager
async def lifespan(app):
    initialize()
    yield

app = FastAPI(title='PROMATH', lifespan=lifespan, docs_url=None if os.environ.get('PROMATH_PRODUCTION') == '1' else '/docs')

@app.middleware('http')
async def security_headers(request, call_next):
    response = await call_next(request)
    response.headers['X-Content-Type-Options'] = 'nosniff'
    response.headers['Cache-Control'] = 'no-store'
    return response

def password_hash(password, salt):
    return hashlib.scrypt(password.encode(), salt=bytes.fromhex(salt), n=32768, r=8, p=1, maxmem=64*1024*1024).hex()

def create_user(username, name, password, role='student'):
    salt = secrets.token_hex(16)
    encoded = password_hash(password, salt)
    user_id = uuid.uuid4().hex
    try:
        with db() as con:
            con.execute('INSERT INTO users VALUES(?,?,?,?,?,?)', (user_id, username.lower(), name, salt, encoded, role))
    except sqlite3.IntegrityError:
        raise HTTPException(409, '이미 사용 중인 아이디입니다.')
    return dict(id=user_id, username=username.lower(), name=name, role=role)

def session_for(user):
    token = secrets.token_urlsafe(32)
    with db() as con:
        con.execute('DELETE FROM sessions WHERE expires < ?', (time.time(),))
        con.execute('INSERT INTO sessions VALUES(?,?,?)', (hashlib.sha256(token.encode()).hexdigest(), user['id'], time.time() + 7*86400))
    return {'token': token, 'user': user}

def public_user(user):
    return {key: user[key] for key in ('id','username','name','role')}

def current_user(request: Request):
    authorization = request.headers.get('authorization', '')
    if not authorization.startswith('Bearer '):
        raise HTTPException(401, '로그인이 필요합니다.')
    token_hash = hashlib.sha256(authorization[7:].encode()).hexdigest()
    with db() as con:
        row = con.execute('SELECT users.* FROM sessions JOIN users ON users.id=sessions.user_id WHERE token_hash=? AND expires>?', (token_hash,time.time())).fetchone()
    if row is None:
        raise HTTPException(401, '로그인이 만료되었습니다.')
    return dict(row, token_hash=token_hash)

def instructor(user=Depends(current_user)):
    if user['role'] != 'instructor':
        raise HTTPException(403, '강사 권한이 필요합니다.')
    return user

def auth_limit(request, username):
    ip = request.client.host
    now = time.time()
    with db() as con:
        con.execute('DELETE FROM attempts WHERE at<?', (now-600,))
        for bucket, limit in ((f'ip:{ip}',100), (f'user:{ip}:{username.lower()}',12)):
            if con.execute('SELECT COUNT(*) FROM attempts WHERE bucket=?', (bucket,)).fetchone()[0] >= limit:
                raise HTTPException(429, '잠시 후 다시 시도해 주세요.')
        con.executemany('INSERT INTO attempts VALUES(?,?)', [(f'ip:{ip}',now),(f'user:{ip}:{username.lower()}',now)])

class Credentials(BaseModel):
    model_config = ConfigDict(extra='forbid')
    username: str = Field(pattern=r'^[A-Za-z0-9_-]{3,32}$')
    password: str = Field(min_length=10, max_length=128)

class Signup(Credentials):
    name: str = Field(min_length=1,max_length=60)

class CourseInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    title: str = Field(min_length=1,max_length=120)
    description: str = Field(default='',max_length=500)

class LessonInput(BaseModel):
    model_config = ConfigDict(extra='forbid')
    title: str = Field(min_length=1,max_length=120)
    stage: int = Field(ge=1,le=8)
    position: int = Field(ge=1,le=999)

class Publication(BaseModel):
    model_config = ConfigDict(extra='forbid')
    published: bool

@app.get('/health')
def health(): return {'status':'ok','version':'0.4.0'}

@app.post('/auth/signup',status_code=201)
def signup(body: Signup, request: Request):
    auth_limit(request, body.username)
    if not body.name.strip(): raise HTTPException(422,'이름을 입력해 주세요.')
    return session_for(create_user(body.username, body.name.strip(), body.password))

@app.post('/auth/login')
def login(body: Credentials, request: Request):
    auth_limit(request,body.username)
    with db() as con:
        user = con.execute('SELECT * FROM users WHERE username=?',(body.username.lower(),)).fetchone()
    salt = user['salt'] if user else '00'*16
    actual = password_hash(body.password,salt)
    if not user or not secrets.compare_digest(actual,user['password_hash']):
        raise HTTPException(401,'아이디 또는 비밀번호가 일치하지 않습니다.')
    return session_for(public_user(user))

@app.get('/me')
def me(user=Depends(current_user)): return public_user(user)

@app.post('/auth/logout')
def logout(user=Depends(current_user)):
    with db() as con: con.execute('DELETE FROM sessions WHERE token_hash=?',(user['token_hash'],))
    return {'ok':True}

def course_access(course_id,user,write=False):
    with db() as con:
        course = con.execute('SELECT * FROM courses WHERE id=?',(course_id,)).fetchone()
    if not course: raise HTTPException(404,'강좌를 찾을 수 없습니다.')
    owned = user['role']=='instructor' and course['owner_id']==user['id']
    if write and not owned: raise HTTPException(403,'이 강좌를 수정할 권한이 없습니다.')
    if not owned and not course['published']: raise HTTPException(404,'강좌를 찾을 수 없습니다.')
    return dict(course), owned

def lesson_access(lesson_id,user,write=False):
    with db() as con:
        lesson = con.execute('SELECT * FROM lessons WHERE id=?',(lesson_id,)).fetchone()
    if not lesson: raise HTTPException(404,'차시를 찾을 수 없습니다.')
    _,owned = course_access(lesson['course_id'],user,write)
    if not owned and not lesson['published']: raise HTTPException(404,'차시를 찾을 수 없습니다.')
    return dict(lesson)

@app.get('/courses')
def courses(user=Depends(current_user)):
    with db() as con:
        rows = con.execute('SELECT c.*, (SELECT COUNT(*) FROM lessons l WHERE l.course_id=c.id AND (l.published=1 OR c.owner_id=?)) AS lesson_count FROM courses c WHERE c.published=1 OR (c.owner_id=? AND ?=\'instructor\') ORDER BY c.rowid DESC',(user['id'],user['id'],user['role'])).fetchall()
    return [dict(row) for row in rows]

@app.post('/courses',status_code=201)
def create_course(body: CourseInput,user=Depends(instructor)):
    if not body.title.strip(): raise HTTPException(422,'강좌명을 입력해 주세요.')
    course_id = uuid.uuid4().hex
    with db() as con: con.execute('INSERT INTO courses VALUES(?,?,?,?,0)',(course_id,user['id'],body.title.strip(),body.description.strip()))
    return course_access(course_id,user)[0]

@app.put('/courses/{course_id}')
@app.patch('/courses/{course_id}')
def publish_course(course_id: str,body: Publication,user=Depends(instructor)):
    course_access(course_id,user,True)
    with db() as con:
        if body.published and not con.execute('SELECT id FROM lessons WHERE course_id=? AND published=1 AND asset_id IS NOT NULL',(course_id,)).fetchone():
            raise HTTPException(409,'파일을 등록하고 공개한 차시가 필요합니다.')
        con.execute('UPDATE courses SET published=? WHERE id=?',(body.published,course_id))
    return course_access(course_id,user)[0]

@app.get('/courses/{course_id}/lessons')
def lessons(course_id: str,user=Depends(current_user)):
    _,owned = course_access(course_id,user)
    with db() as con:
        rows = con.execute('SELECT l.*, a.filename, a.mime, a.size FROM lessons l LEFT JOIN assets a ON a.id=l.asset_id WHERE course_id=? AND (l.published=1 OR ?=1) ORDER BY l.stage,l.position,l.rowid',(course_id,int(owned))).fetchall()
    return [dict(row) for row in rows]

@app.post('/courses/{course_id}/lessons',status_code=201)
def create_lesson(course_id: str,body: LessonInput,user=Depends(instructor)):
    course_access(course_id,user,True)
    if not body.title.strip(): raise HTTPException(422,'차시명을 입력해 주세요.')
    lesson_id = uuid.uuid4().hex
    with db() as con: con.execute('INSERT INTO lessons VALUES(?,?,?,?,?,0,NULL)',(lesson_id,course_id,body.title.strip(),body.stage,body.position))
    return lesson_access(lesson_id,user)

@app.put('/lessons/{lesson_id}')
@app.patch('/lessons/{lesson_id}')
def publish_lesson(lesson_id: str,body: Publication,user=Depends(instructor)):
    lesson = lesson_access(lesson_id,user,True)
    if body.published and not lesson['asset_id']: raise HTTPException(409,'강의 파일을 먼저 업로드해 주세요.')
    with db() as con: con.execute('UPDATE lessons SET published=? WHERE id=?',(body.published,lesson_id))
    return lesson_access(lesson_id,user)

@app.post('/lessons/{lesson_id}/file',status_code=201)
async def upload(lesson_id: str,request: Request,user=Depends(instructor)):
    lesson_access(lesson_id,user,True)
    types = {'video/mp4':'.mp4','video/webm':'.webm','application/pdf':'.pdf'}
    mime = request.headers.get('content-type','').split(';')[0]
    if mime not in types: raise HTTPException(415,'MP4, WEBM, PDF 파일만 등록할 수 있습니다.')
    try: declared = int(request.headers.get('content-length','0'))
    except ValueError: raise HTTPException(400,'파일 크기가 올바르지 않습니다.')
    if not 0 < declared <= MAX_UPLOAD: raise HTTPException(413,'파일은 512MB 이하여야 합니다.')
    asset_id = uuid.uuid4().hex
    storage_name = asset_id + types[mime]
    target = DATA / 'uploads' / storage_name
    temporary = target.with_suffix('.part')
    filename = unquote(request.headers.get('x-filename','lecture'+types[mime])).replace('\\','/').split('/')[-1][:180]
    count = 0
    prefix = b''
    try:
        with temporary.open('xb') as out:
            async for chunk in request.stream():
                count += len(chunk)
                if count > MAX_UPLOAD or count > declared: raise HTTPException(413,'파일이 허용 크기를 초과했습니다.')
                prefix = (prefix + chunk[:32])[:32]
                out.write(chunk)
        valid = (mime=='video/mp4' and prefix[4:8]==b'ftyp') or (mime=='video/webm' and prefix[:4]==b'\x1a\x45\xdf\xa3') or (mime=='application/pdf' and prefix.startswith(b'%PDF-'))
        if count != declared or not valid: raise HTTPException(400,'파일 형식 또는 크기를 확인해 주세요.')
        temporary.replace(target)
        with db() as con:
            previous = con.execute('SELECT a.storage_name FROM lessons l JOIN assets a ON a.id=l.asset_id WHERE l.id=?',(lesson_id,)).fetchone()
            con.execute('INSERT INTO assets VALUES(?,?,?,?,?,?)',(asset_id,lesson_id,filename,mime,count,storage_name))
            con.execute('UPDATE lessons SET asset_id=? WHERE id=?',(asset_id,lesson_id))
        if previous:
            with contextlib.suppress(OSError): (DATA/'uploads'/previous['storage_name']).unlink()
        return {'asset_id':asset_id,'filename':filename,'mime':mime,'size':count}
    except BaseException:
        with contextlib.suppress(OSError): temporary.unlink()
        with contextlib.suppress(OSError): target.unlink()
        raise

@app.get('/lessons/{lesson_id}/file')
def file(lesson_id: str,user=Depends(current_user)):
    lesson = lesson_access(lesson_id,user)
    with db() as con: asset = con.execute('SELECT * FROM assets WHERE id=?',(lesson['asset_id'],)).fetchone()
    if not asset: raise HTTPException(404,'등록된 강의 파일이 없습니다.')
    return FileResponse(DATA/'uploads'/asset['storage_name'],media_type=asset['mime'],filename=asset['filename'],content_disposition_type='inline')

if __name__=='__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command',choices=['create-instructor'])
    parser.add_argument('--username',required=True)
    parser.add_argument('--name',required=True)
    args = parser.parse_args()
    import re
    if not re.fullmatch(r'[A-Za-z0-9_-]{3,32}',args.username): parser.error('아이디는 영문·숫자·_·- 3~32자입니다.')
    password = getpass.getpass('강사 비밀번호 (10자 이상): ')
    if not 10 <= len(password) <= 128 or password != getpass.getpass('비밀번호 확인: '): parser.error('비밀번호 길이 또는 확인 값을 확인하세요.')
    initialize()
    create_user(args.username,args.name,password,'instructor')
    print('강사 계정이 생성되었습니다.')
