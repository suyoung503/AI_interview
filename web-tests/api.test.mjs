import { test } from 'node:test';
import assert from 'node:assert/strict';

import { createApi, StaleResponse } from '../src/main/resources/static/api.js';

const token = (value) => Response.json({ token: value });

test('sends a token only on protected requests and clears it on logout', async () => {
    const headers = [];
    const api = createApi(async (url, options) => {
        headers.push(new Headers(options.headers).get('Authorization'));
        return url === '/api/auth/login' ? token('jwt') : Response.json([]);
    });
    await api.authenticate('login', { email: 'test@example.test', password: 'test' });
    await api.request('/api/resumes');
    api.logout();
    await assert.rejects(api.request('/api/resumes'), { name: 'StaleResponse' });
    assert.deepEqual(headers, [null, 'Bearer jwt']);
});

test('reports HTTP errors without automatic retries or dropping token on provider errors', async () => {
    let calls = 0;
    const api = createApi(async (url) => {
        if (url === '/api/auth/login') return token('jwt');
        calls++;
        return Response.json({ message: '잠시 후 시도해 주세요.' }, { status: 502 });
    });
    await api.authenticate('login', {});
    await assert.rejects(api.request('/api/answers', 'POST', {}), {
        name: 'ApiError', status: 502, message: '잠시 후 시도해 주세요.'
    });
    assert.equal(calls, 1);
    assert.equal(api.signedIn, true);
});

for (const status of [401, 403]) {
    test(`protected ${status} clears token but invalid login reports its own error`, async () => {
        let valid = false;
        const api = createApi(async (url) => {
            if (url !== '/api/auth/login') return new Response(null, { status });
            return valid ? token('jwt') : Response.json({ message: '비밀번호를 확인해 주세요.' }, { status: 401 });
        });
        await assert.rejects(api.authenticate('login', {}), {
            name: 'ApiError', status: 401, message: '비밀번호를 확인해 주세요.'
        });
        valid = true;
        await api.authenticate('login', {});
        await assert.rejects(api.request('/api/sessions'), { name: 'ApiError', status });
        assert.equal(api.signedIn, false);
    });
}

test('ignores a late unauthorized response from the previous account', async () => {
    let release;
    const apis = [];
    const api = createApi(async (url, options) => {
        if (url === '/api/auth/login') return token(JSON.parse(options.body).email);
        apis.push(options.headers.Authorization);
        return apis.length === 1
            ? new Promise((resolve) => { release = resolve; })
            : Response.json([]);
    });
    await api.authenticate('login', { email: 'previous' });
    const late = api.request('/api/sessions');
    api.logout();
    await api.authenticate('login', { email: 'current' });
    release(new Response(null, { status: 403 }));
    await assert.rejects(late, StaleResponse);
    await api.request('/api/sessions');
    assert.deepEqual(apis, ['Bearer previous', 'Bearer current']);
});

test('a login response arriving after logout cannot set a token', async () => {
    let release;
    const api = createApi(() => new Promise((resolve) => { release = resolve; }));
    const late = api.authenticate('login', {});
    api.logout();
    release(token('late'));
    await assert.rejects(late, StaleResponse);
    assert.equal(api.signedIn, false);
});

test('ignores an old response if the account changes while its JSON body is being read', async () => {
    let release, reading;
    const bodyStarted = new Promise((resolve) => { reading = resolve; });
    const api = createApi(async (url) => {
        if (url === '/api/auth/login') return token('jwt');
        return {
            ok: true,
            json: () => new Promise((resolve) => { release = resolve; reading(); })
        };
    });
    await api.authenticate('login', {});
    const late = api.request('/api/resumes');
    await bodyStarted;
    api.logout();
    await api.authenticate('login', {});
    release([{ id: 1, text: '이전 계정의 이력서' }]);
    await assert.rejects(late, StaleResponse);
    assert.equal(api.signedIn, true);
});
