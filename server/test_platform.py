import tempfile
import unittest
from pathlib import Path
import sqlite3
from fastapi.testclient import TestClient
import app as platform

class PlatformTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        platform.DATA = Path(self.temp.name)
        self.client_context = TestClient(platform.app)
        self.client = self.client_context.__enter__()
        teacher = platform.create_user('teacher','강사','Teacher-password-2026','instructor')
        self.teacher = platform.session_for(teacher)['token']
        self.student = self.client.post('/auth/signup',json={'username':'student','name':'학생','password':'Student-password-2026'}).json()['token']
    def tearDown(self):
        self.client_context.__exit__(None,None,None)
        self.temp.cleanup()
    def headers(self, token=None): return {'Authorization':'Bearer '+(token or self.teacher)}
    def course_and_lesson(self):
        c = self.client.post('/courses',headers=self.headers(),json={'title':'고등학생을 위한 초중등 복습'}).json()
        l = self.client.post('/courses/'+c['id']+'/lessons',headers=self.headers(),json={'title':'일차방정식의 개념','stage':3,'position':1}).json()
        return c,l
    def upload(self, l, data=b'%PDF-1.7\nplatform-test', token=None):
        return self.client.post('/lessons/'+l['id']+'/file',headers={**self.headers(token),'Content-Type':'application/pdf','X-Filename':'lecture.pdf'},content=data)
    def test_signup_cannot_request_instructor_role(self):
        r=self.client.post('/auth/signup',json={'username':'attacker','name':'a','password':'long-password','role':'instructor'})
        self.assertEqual(r.status_code,422)
        self.assertEqual(self.client.get('/me',headers=self.headers(self.student)).json()['role'],'student')
    def test_passwords_are_hashed_and_duplicate_user_is_rejected(self):
        with platform.db() as con:
            row=con.execute("SELECT * FROM users WHERE username='student'").fetchone()
        self.assertNotEqual(row['password_hash'],'Student-password-2026')
        self.assertEqual(self.client.post('/auth/signup',json={'username':'STUDENT','name':'학생','password':'Student-password-2026'}).status_code,409)
    def test_bad_password_and_revoked_session_are_rejected(self):
        self.assertEqual(self.client.post('/auth/login',json={'username':'student','password':'wrong-password'}).status_code,401)
        self.assertEqual(self.client.post('/auth/logout',headers=self.headers(self.student)).status_code,200)
        self.assertEqual(self.client.get('/me',headers=self.headers(self.student)).status_code,401)
    def test_student_cannot_create_upload_or_publish(self):
        c,l=self.course_and_lesson()
        self.assertEqual(self.client.post('/courses',headers=self.headers(self.student),json={'title':'x'}).status_code,403)
        self.assertEqual(self.upload(l,token=self.student).status_code,403)
        self.assertEqual(self.client.put('/lessons/'+l['id'],headers=self.headers(self.student),json={'published':True}).status_code,403)
    def test_teacher_cannot_edit_another_teachers_course(self):
        c,l=self.course_and_lesson()
        token=platform.session_for(platform.create_user('other','다른 강사','Other-password-2026','instructor'))['token']
        self.assertEqual(self.upload(l,token=token).status_code,403)
    def test_drafts_are_hidden_and_publish_requires_a_file(self):
        c,l=self.course_and_lesson()
        self.assertEqual(self.client.get('/courses',headers=self.headers(self.student)).json(),[])
        self.assertEqual(self.client.get('/courses/'+c['id']+'/lessons',headers=self.headers(self.student)).status_code,404)
        self.assertEqual(self.client.put('/lessons/'+l['id'],headers=self.headers(),json={'published':True}).status_code,409)
        self.assertEqual(self.client.put('/courses/'+c['id'],headers=self.headers(),json={'published':True}).status_code,409)
    def test_uploaded_published_course_is_visible_and_pdf_is_protected(self):
        c,l=self.course_and_lesson()
        self.assertEqual(self.upload(l).status_code,201)
        self.client.put('/lessons/'+l['id'],headers=self.headers(),json={'published':True})
        self.client.put('/courses/'+c['id'],headers=self.headers(),json={'published':True})
        self.assertEqual(len(self.client.get('/courses',headers=self.headers(self.student)).json()),1)
        lessons=self.client.get('/courses/'+c['id']+'/lessons',headers=self.headers(self.student)).json()
        self.assertEqual(lessons[0]['mime'],'application/pdf')
        self.assertEqual(self.client.get('/lessons/'+l['id']+'/file').status_code,401)
        self.assertEqual(self.client.get('/lessons/'+l['id']+'/file',headers=self.headers(self.student)).content,b'%PDF-1.7\nplatform-test')
        self.client.put('/courses/'+c['id'],headers=self.headers(),json={'published':False})
        self.assertEqual(self.client.get('/lessons/'+l['id']+'/file',headers=self.headers(self.student)).status_code,404)
    def test_invalid_file_is_rejected_without_replacing_previous_file(self):
        c,l=self.course_and_lesson(); self.upload(l)
        self.assertEqual(self.upload(l,data=b'executable-not-a-pdf').status_code,400)
        self.assertEqual(self.client.get('/lessons/'+l['id']+'/file',headers=self.headers()).content,b'%PDF-1.7\nplatform-test')
        self.assertFalse(list((platform.DATA/'uploads').glob('*.part')))
    def test_range_requests_and_restart_persistence(self):
        c,l=self.course_and_lesson(); self.upload(l)
        platform.initialize()
        r=self.client.get('/lessons/'+l['id']+'/file',headers={**self.headers(),'Range':'bytes=0-4'})
        self.assertEqual(r.status_code,206); self.assertEqual(r.content,b'%PDF-')
        self.assertEqual(self.client.get('/me',headers=self.headers(self.teacher)).status_code,200)
    def test_auth_rate_limit(self):
        for _ in range(12): self.client.post('/auth/login',json={'username':'bad','password':'invalid-password'})
        self.assertEqual(self.client.post('/auth/login',json={'username':'bad','password':'invalid-password'}).status_code,429)
    def test_expired_session(self):
        with platform.db() as con: con.execute('UPDATE sessions SET expires=0')
        self.assertEqual(self.client.get('/me',headers=self.headers(self.teacher)).status_code,401)
    def test_oversized_and_unsupported_uploads(self):
        c,l=self.course_and_lesson()
        r=self.client.post('/lessons/'+l['id']+'/file',headers={**self.headers(),'Content-Type':'application/pdf','Content-Length':str(platform.MAX_UPLOAD+1)},content=b'%PDF-')
        self.assertEqual(r.status_code,413)
        r=self.client.post('/lessons/'+l['id']+'/file',headers={**self.headers(),'Content-Type':'text/html'},content=b'<script>')
        self.assertEqual(r.status_code,415)

if __name__=='__main__': unittest.main()
