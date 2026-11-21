export class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
    }
}

export class StaleResponse extends Error {
    constructor() {
        super('이전 로그인에서 시작한 요청입니다.');
        this.name = 'StaleResponse';
    }
}

export function createApi(fetcher = globalThis.fetch.bind(globalThis)) {
    let token = null;
    let generation = 0;
    function logout() {
        token = null;
        generation++;
    }

    async function send(path, method, body, protectedRequest, expected) {
        const headers = { Accept: 'application/json' };
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        if (protectedRequest) headers.Authorization = `Bearer ${token}`;
        const check = () => { if (generation !== expected) throw new StaleResponse(); };
        let response;
        try {
            response = await fetcher(path, {
                method, headers, cache: 'no-store', redirect: 'error',
                body: body === undefined ? undefined : JSON.stringify(body)
            });
        } catch {
            check();
            throw new ApiError(0, '서버에 연결하지 못했습니다. 연결을 확인해 주세요.');
        }
        check();
        let data;
        try {
            data = await response.json();
        } catch {
            // Authentication failures may have an empty body.
        }
        check();
        if (!response.ok) {
            if (protectedRequest && [401, 403].includes(response.status)) {
                logout();
                throw new ApiError(response.status, '로그인 상태가 만료되었습니다. 다시 로그인해 주세요.');
            }
            throw new ApiError(response.status,
                typeof data?.message === 'string' ? data.message : '요청을 처리하지 못했습니다. 다시 확인해 주세요.');
        }
        return data;
    }

    return {
        get signedIn() { return token !== null; },
        logout,
        async authenticate(action, credentials) {
            logout();
            const current = generation;
            const data = await send(`/api/auth/${action}`, 'POST', credentials, false, current);
            if (generation !== current) throw new StaleResponse();
            if (typeof data?.token !== 'string' || !data.token) {
                throw new ApiError(0, '로그인 응답을 확인할 수 없습니다.');
            }
            token = data.token;
        },
        async request(path, method = 'GET', body) {
            if (!token) throw new StaleResponse();
            return send(path, method, body, true, generation);
        }
    };
}
