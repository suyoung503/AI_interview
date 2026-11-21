import { createApi, StaleResponse } from './api.js';

const api = createApi();
const view = document.querySelector('#view');
const nav = document.querySelector('#navigation');
const status = document.querySelector('#status');
const dateFormat = new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' });
const formatDate = (value) => value && Number.isFinite(Date.parse(value))
    ? dateFormat.format(new Date(value)) : '일시 미기록';

function initialState() {
    return {
        page: 'resume', mode: 'login', email: '', resumeDraft: '', resumes: [], selected: null,
        history: [], session: null, pendingSession: null, rootId: null, drafts: new Map(),
        accepted: new Set(), authenticated: false, busy: false
    };
}
let state = initialState();

function el(tag, className = '', text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined && text !== null) node.textContent = text;
    return node;
}

function button(text, action, className = 'secondary') {
    const node = el('button', className, text);
    node.type = 'button';
    if (action) node.addEventListener('click', action);
    return node;
}

function formWith(submit) {
    const form = el('form');
    form.addEventListener('submit', (event) => {
        event.preventDefault();
        submit();
    });
    return form;
}

function labelFor(id, text) {
    const label = el('label', '', text);
    label.htmlFor = id;
    return label;
}

function announce(text = '', error = false) {
    status.textContent = text;
    status.classList.toggle('error', error);
}

function reset(message, error = false) {
    api.logout();
    state = initialState();
    render();
    announce(message, error);
    view.querySelector('#email')?.focus();
}

function lock(active) {
    view.inert = active;
    view.setAttribute('aria-busy', String(active));
    for (const node of nav.querySelectorAll('button:not(.logout)')) node.disabled = active;
}

async function run(progress, action, { errorMessage, successMessage } = {}) {
    if (state.busy) return;
    const current = state;
    const focusId = document.activeElement?.id;
    current.busy = true;
    announce(progress);
    lock(true);
    try {
        await action(current);
        if (current === state) announce(successMessage);
    } catch (error) {
        if (current !== state || error instanceof StaleResponse) return;
        if (current.authenticated && !api.signedIn) {
            reset(error.message, true);
            return;
        }
        const message = typeof error.status === 'number' ? error.message : '응답을 표시하지 못했습니다. 기록을 다시 열어 주세요.';
        announce(errorMessage ? errorMessage(current, error, message) : message, true);
    } finally {
        if (current === state) {
            current.busy = false;
            render();
            const prior = focusId && document.getElementById(focusId);
            (prior && !prior.disabled ? prior : view.querySelector('h1'))?.focus();
        }
    }
}

function heading(title, detail) {
    const top = el('div', 'view-heading');
    const h1 = el('h1', '', title);
    h1.tabIndex = -1;
    top.append(h1, el('p', 'muted', detail));
    return top;
}

function authView() {
    const signup = state.mode === 'signup';
    const mode = state.mode;
    const wrap = el('div', 'auth-grid');
    const intro = el('section', 'intro');
    intro.append(heading('내 경험으로 준비하는\n기술 면접', '이력서에 적은 프로젝트와 기술을 바탕으로 질문을 받고, 답변에 대한 피드백을 확인하세요.'));
    const steps = el('ol', 'steps');
    for (const [title, description] of [
        ['이력서 준비', '텍스트를 붙여넣거나 저장한 이력서를 선택하세요.'],
        ['기술 질문 연습', '기본 질문 5개에 답하고 필요한 경우 꼬리 질문을 받아요.'],
        ['평가와 기록 확인', '기술적 정확성, 근거, 설명력을 살피고 다시 연습하세요.']
    ]) {
        const step = el('li');
        step.append(el('strong', '', title), el('p', '', description));
        steps.append(step);
    }
    intro.append(steps);

    const card = el('section', 'panel auth-panel');
    const email = el('input');
    email.id = 'email';
    email.type = 'email';
    email.required = true;
    email.autocomplete = 'username';
    email.placeholder = 'name@example.com';
    email.value = state.email;
    const password = el('input');
    password.id = 'password';
    password.type = 'password';
    password.required = true;
    password.autocomplete = signup ? 'new-password' : 'current-password';
    const form = formWith(() => {
        const credentials = { email: email.value.trim(), password: password.value };
        run(signup ? '계정을 만드는 중…' : '로그인하는 중…', async (current) => {
            current.email = credentials.email;
            await api.authenticate(mode, credentials);
            if (current !== state) return;
            current.authenticated = true;
            current.email = '';
            render();
            current.resumes = await api.request('/api/resumes');
        });
    });
    const submit = button(signup ? '회원가입' : '로그인', null, 'primary');
    submit.type = 'submit';
    const change = button(signup ? '로그인으로 이동' : '회원가입으로 이동', () => {
        state.email = email.value;
        state.mode = signup ? 'login' : 'signup';
        announce();
        render();
        view.querySelector('#email').focus();
    }, 'link');
    form.append(labelFor('email', '이메일'), email, labelFor('password', '비밀번호'), password, submit);
    card.append(el('h2', '', signup ? '계정 만들기' : '로그인'), form, change,
        el('p', 'fine', '새로고침하면 보안을 위해 다시 로그인해야 합니다.'));
    wrap.append(intro, card);
    return wrap;
}

function textInput(id, label, value, max, update) {
    const wrap = el('div', 'text-field');
    const input = el('textarea');
    input.id = id;
    input.required = true;
    input.rows = id === 'resume-text' ? 11 : 6;
    input.value = value;
    const count = el('span', 'fine');
    count.id = `${id}-count`;
    input.setAttribute('aria-describedby', count.id);
    const sync = () => {
        const size = Array.from(input.value).length;
        count.textContent = `${size.toLocaleString('ko-KR')} / ${max.toLocaleString('ko-KR')}자`;
        count.classList.toggle('over-limit', size > max);
        input.setCustomValidity(size > max ? `${max.toLocaleString('ko-KR')}자 이하로 입력해 주세요.`
            : !input.value.trim() && input.value ? '내용을 입력해 주세요.' : '');
    };
    input.addEventListener('input', () => { update(input.value); sync(); });
    wrap.append(labelFor(id, label), input, count);
    sync();
    return { wrap, input };
}

function empty(text) {
    return el('p', 'empty', text);
}

function chooseResume(id) {
    return run('이력서를 불러오는 중…', async (current) => {
        current.selected = await api.request(`/api/resumes/${id}`);
    });
}

function resumeView() {
    const wrap = el('section');
    wrap.append(heading('이력서 준비', '연습에 사용할 이력서를 저장하거나 선택하세요.'));
    const grid = el('div', 'two-column');
    const editor = el('section', 'panel');
    const { wrap: field, input } = textInput('resume-text', '이력서 텍스트', state.resumeDraft, 20000,
        (value) => { state.resumeDraft = value; });
    input.placeholder = '프로젝트에서 맡은 역할, 사용한 기술, 선택 이유와 결과를 적어 보세요.';
    const save = button('이력서 저장', null, 'primary');
    save.type = 'submit';
    save.id = 'save-resume';
    const form = formWith(() => run('이력서를 저장하는 중…', async (current) => {
        const saved = await api.request('/api/resumes', 'POST', { text: current.resumeDraft });
        current.selected = saved;
        current.resumes = [saved, ...current.resumes];
        current.resumeDraft = '';
    }, { successMessage: '이력서를 저장하고 선택했습니다.' }));
    form.append(field, el('p', 'fine', '이력서는 계정에 저장됩니다. 면접을 시작하면 선택한 이력서가 질문 생성을 위해 AI 서비스에 전송됩니다. 개인정보는 제외해 주세요.'), save);
    editor.append(el('h2', '', '새 이력서'), form);

    const saved = el('section', 'panel');
    saved.append(el('h2', '', '저장한 이력서'));
    if (state.resumes.length === 0) saved.append(empty('아직 저장한 이력서가 없습니다.'));
    else {
        const list = el('ul', 'record-list');
        for (const resume of state.resumes) {
            const row = el('li');
            const selected = state.selected?.id === resume.id;
            const pick = button(`이력서 #${resume.id}`, () => chooseResume(resume.id), 'record-button');
            pick.id = `resume-${resume.id}`;
            pick.setAttribute('aria-pressed', String(selected));
            pick.append(el('span', 'fine', formatDate(resume.createdAt)));
            row.append(pick);
            list.append(row);
        }
        saved.append(list);
    }
    if (state.selected) {
        const selected = el('details', 'details resume-preview');
        selected.append(el('summary', '', `선택한 이력서 #${state.selected.id} 미리 보기`),
            el('p', 'long-text', state.selected.text));
        const start = button('면접 시작', startInterview, 'primary');
        start.id = 'start-interview';
        if (state.pendingSession !== null) {
            saved.append(el('p', 'notice', '이미 생성된 면접이 있습니다. 새로 시작하기 전에 불러와 주세요.'),
                button('생성한 면접 열기', () => openSession(state.pendingSession)));
        } else saved.append(selected, start);
    }
    grid.append(editor, saved);
    wrap.append(grid);
    return wrap;
}

async function loadSession(current, id) {
    const session = await api.request(`/api/sessions/${id}`);
    if (current.session?.id !== session.id) current.rootId = null;
    current.session = session;
    if (current.pendingSession === id) current.pendingSession = null;
    current.page = 'interview';
}

function startInterview() {
    run('이력서를 바탕으로 기본 질문 5개를 만드는 중…', async (current) => {
        const created = await api.request('/api/sessions', 'POST', { resumeId: current.selected.id });
        current.pendingSession = created.id;
        await loadSession(current, created.id);
    }, {
        errorMessage: (current, error, message) => current.pendingSession !== null
            ? '면접을 생성했지만 질문을 불러오지 못했습니다. 생성한 면접을 열거나 면접 기록에서 확인해 주세요.' : message
    });
}

function openSession(id) {
    run('면접을 불러오는 중…', (current) => loadSession(current, id));
}

function showResumes() {
    state.page = 'resume';
    render();
    run('저장한 이력서를 불러오는 중…', async (current) => {
        current.resumes = await api.request('/api/resumes');
    });
}

function showHistory() {
    state.page = 'history';
    render();
    run('면접 기록을 불러오는 중…', async (current) => {
        current.history = await api.request('/api/sessions');
    });
}

function historyView() {
    const wrap = el('section');
    wrap.append(heading('면접 기록', '진행 중인 면접을 이어서 연습하거나 완료한 평가를 다시 읽어 보세요.'));
    const panel = el('section', 'panel');
    panel.append(el('h2', '', '내 면접'));
    if (!state.history.length) panel.append(empty('표시할 면접 기록이 없습니다. 이력서를 선택해 면접을 시작해 보세요.'));
    else {
        const list = el('ul', 'record-list');
        for (const session of state.history) {
            const row = el('li', 'history-row');
            const info = el('div');
            info.append(el('strong', '', `면접 #${session.id}`));
            if (session.categoryName) info.append(el('span', 'category', session.categoryName));
            info.append(el('p', 'fine', formatDate(session.createdAt)));
            const done = session.status === 'DONE';
            const readOnly = done || session.resumeId == null || !session.criteriaVersion;
            const actions = el('div', 'row-actions');
            actions.append(el('span', `badge ${done ? 'done' : ''}`, done ? '완료' : readOnly ? '기존 기록' : '진행 중'),
                button(readOnly ? '기록 보기' : '이어하기', () => openSession(session.id)));
            row.append(info, actions);
            list.append(row);
        }
        panel.append(list);
    }
    wrap.append(panel);
    return wrap;
}

function key(session, question) {
    return `${session.id}:${question.id}`;
}

function answerForm(session, question) {
    const draftKey = key(session, question);
    const { wrap, input } = textInput(`answer-${question.id}`, '내 답변', state.drafts.get(draftKey) ?? '', 10000,
        (text) => { state.drafts.set(draftKey, text); });
    input.placeholder = '어떤 선택을 했고, 왜 그렇게 판단했는지 설명해 보세요.';
    const submit = button('답변 제출', null, 'primary');
    submit.type = 'submit';
    submit.id = `submit-${question.id}`;
    const form = formWith(() => run('답변을 평가하는 중…', async (current) => {
        await api.request('/api/answers', 'POST',
            { sessionId: session.id, questionId: question.id, userAnswer: current.drafts.get(draftKey) }, { expectJson: false });
        current.accepted.add(draftKey);
        current.drafts.delete(draftKey);
        await loadSession(current, session.id);
    }, {
        errorMessage: (current, error, message) => current.accepted.has(draftKey)
            ? '답변은 제출됐지만 평가를 불러오지 못했습니다. 다시 제출하지 말고 기록을 새로 불러와 주세요.'
            : error.status === 409 || error.status === 400
                ? `${message} 이미 제출했거나 완료한 면접이라면 기록을 새로 불러와 주세요.` : message,
        successMessage: '답변을 제출했습니다.'
    }));
    form.append(wrap, submit);
    return form;
}

function questionBlock(session, question, isChild, readOnly) {
    const section = el('section', 'question-block');
    section.append(el('p', 'question-label', isChild ? '꼬리 질문' : '기본 질문'),
        el('h2', 'question-title', question.content));
    if (question.sourceQuote) {
        const quote = el('blockquote');
        quote.append(el('p', 'quote-label', '이력서에서'),
            el('p', 'long-text', question.sourceQuote));
        section.append(quote);
    }
    if (question.intent || question.evaluationCriteria?.length) {
        const criteria = el('details', 'details');
        criteria.append(el('summary', '', '질문 의도와 평가 기준'));
        if (question.intent) criteria.append(el('p', '', question.intent));
        if (question.evaluationCriteria?.length) criteria.append(listItems(question.evaluationCriteria));
        section.append(criteria);
    }
    const answers = question.answers ?? [];
    if (answers.length) {
        answers.forEach((answer, index) => {
            const written = el('div', 'written');
            written.append(el('h3', '', answers.length === 1 ? '제출한 답변' : `제출한 답변 ${index + 1}`),
                el('p', 'long-text', answer.userAnswer));
            section.append(written);
        });
    } else if (state.accepted.has(key(session, question))) {
        section.append(el('p', 'notice', '답변을 제출했습니다. 평가를 확인하려면 기록을 새로 불러와 주세요.'));
    } else if (readOnly || !question.evaluationCriteria?.length) {
        section.append(empty('저장된 답변이 없습니다.'));
    } else section.append(answerForm(session, question));
    return section;
}

function listItems(values) {
    const list = el('ul');
    for (const value of values) list.append(el('li', '', value));
    return list;
}

const verdicts = {
    CORRECT: '정확함', INCORRECT: '오류 있음', NOT_ASSESSABLE: '판단할 정보 부족',
    SUFFICIENT: '충분함', INSUFFICIENT: '부족함', INVALID: '타당하지 않음',
    CLEAR: '명확함', UNCLEAR: '불명확함'
};

function evaluationCard(answer, index, total) {
    const card = el('section', 'evaluation');
    const stage = answer.evaluationStage === 'COMPREHENSIVE' ? '종합 평가'
        : answer.evaluationStage === 'INITIAL' ? '최초 평가' : '기존 평가 기록';
    card.append(el('h3', '', total > 1 ? `${stage} ${index + 1}` : stage));
    const evaluation = answer.evaluation;
    if (!evaluation) {
        if (answer.score != null) card.append(el('p', 'score', `점수 ${answer.score}`));
        card.append(el('p', 'long-text', answer.feedback || '저장된 피드백이 없습니다.'));
        return card;
    }
    const axes = el('dl', 'axes');
    for (const [label, value] of [
        ['기술적 정확성', evaluation.technicalAccuracy], ['근거', evaluation.reasoning], ['설명력', evaluation.clarity]
    ]) {
        const line = el('div');
        line.append(el('dt', '', label), el('dd', '', Object.hasOwn(verdicts, value) ? verdicts[value] : '미기록'));
        axes.append(line);
    }
    card.append(axes);
    for (const [label, items] of [
        ['강점', evaluation.strengths], ['개선할 점', evaluation.improvements], ['불확실한 점', evaluation.uncertainties]
    ]) {
        const group = el('div', 'feedback-group');
        group.append(el('h4', '', label), items?.length ? listItems(items) : el('p', 'fine', '기록된 항목 없음'));
        card.append(group);
    }
    return card;
}

function finish(session) {
    run('면접을 완료하는 중…', async (current) => {
        await api.request(`/api/sessions/${session.id}/done`, 'PATCH', undefined, { expectJson: false });
        current.session = { ...current.session, status: 'DONE' };
        await loadSession(current, session.id);
    }, {
        successMessage: '면접을 완료했습니다.',
        errorMessage: (current, error, message) => current.session.status === 'DONE'
            ? '면접은 완료됐지만 최신 기록을 불러오지 못했습니다. 기록을 새로 불러와 주세요.' : message
    });
}

function interviewView() {
    const session = state.session;
    const questions = session.questions ?? [];
    const roots = questions.filter((question) => question.parentQuestionId == null);
    const active = roots.find((question) => question.id === state.rootId) ?? roots[0];
    const done = session.status === 'DONE';
    const readOnly = done || session.resumeId == null || !session.criteriaVersion;
    const answered = roots.filter((root) => (root.answers ?? []).length || state.accepted.has(key(session, root))).length;
    const screen = el('section');
    const top = el('div', 'interview-head');
    const title = heading(done ? '완료한 면접' : readOnly ? '기존 면접 기록' : '면접 연습',
        `면접 #${session.id} / ${formatDate(session.createdAt)}`);
    const actions = el('div', 'row-actions');
    const refresh = button('기록 새로 불러오기', () => openSession(session.id));
    refresh.id = 'refresh-session';
    actions.append(refresh);
    top.append(title, actions);
    screen.append(top);
    if (!readOnly) {
        const confirmation = el('section', 'notice');
        confirmation.id = 'finish-confirmation';
        confirmation.hidden = true;
        const controls = el('div', 'row-actions');
        const approve = button('완료하기', () => finish(session), 'primary');
        const cancel = button('취소', () => {
            confirmation.hidden = true;
            complete.setAttribute('aria-expanded', 'false');
            complete.focus();
        });
        const complete = button('면접 완료', () => {
            confirmation.hidden = false;
            complete.setAttribute('aria-expanded', 'true');
            cancel.focus();
        });
        complete.setAttribute('aria-controls', confirmation.id);
        complete.setAttribute('aria-expanded', 'false');
        controls.append(approve, cancel);
        confirmation.append(el('h2', 'confirm-title', '면접을 완료하시겠어요?'),
            el('p', '', '완료하면 답변을 추가할 수 없습니다.'), controls);
        actions.append(complete);
        screen.append(confirmation);
    }
    if (session.resume?.text) {
        const resume = el('details', 'details session-resume');
        resume.append(el('summary', '', '면접에 사용한 이력서'), el('p', 'long-text', session.resume.text));
        screen.append(resume);
    }
    if (!active) {
        screen.append(empty('저장된 질문이 없습니다.'));
        return screen;
    }

    const grid = el('div', 'workspace');
    const navigator = el('nav', 'question-nav panel');
    navigator.setAttribute('aria-label', '기본 질문 탐색');
    navigator.append(el('h2', '', '질문 탐색'), el('p', 'fine', `기본 질문 ${answered} / ${roots.length} 답변`));
    const list = el('ol', 'questions');
    roots.forEach((question, index) => {
        const item = el('li');
        const selected = question.id === active.id;
        const choose = button(`질문 ${index + 1}`, () => {
            state.rootId = question.id;
            announce();
            render();
            document.querySelector(`#question-${question.id}`).focus();
        }, 'question-button');
        choose.id = `question-${question.id}`;
        if (selected) choose.setAttribute('aria-current', 'true');
        if (question.answers?.length || state.accepted.has(key(session, question))) {
            choose.append(el('span', 'question-check', '답변함'));
        }
        item.append(choose);
        list.append(item);
    });
    navigator.append(list);

    const center = el('div', 'panel interview-panel');
    const children = questions.filter((question) => question.parentQuestionId === active.id);
    const group = [active, ...children];
    for (const question of group) center.append(questionBlock(session, question, question !== active, readOnly));

    const aside = el('aside', 'panel evaluation-panel');
    aside.append(el('h2', '', '답변 평가'));
    for (const question of group) {
        const answers = question.answers ?? [];
        answers.forEach((answer, index) => aside.append(evaluationCard(answer, index, answers.length)));
    }
    if (!group.some((question) => question.answers?.length)) {
        aside.append(empty(readOnly ? '저장된 평가가 없습니다.' : '답변을 제출하면 세 가지 기준에 대한 평가가 여기에 표시됩니다.'));
    }
    grid.append(navigator, center, aside);
    screen.append(grid);
    return screen;
}

function render() {
    nav.replaceChildren();
    if (!api.signedIn) {
        view.replaceChildren(authView());
    } else {
        const resumes = button('이력서', showResumes, 'nav-button');
        const history = button('면접 기록', showHistory, 'nav-button');
        resumes.id = 'nav-resumes';
        history.id = 'nav-history';
        if (state.page === 'resume') resumes.setAttribute('aria-current', 'page');
        if (state.page === 'history') history.setAttribute('aria-current', 'page');
        nav.append(resumes, history, button('로그아웃', () => reset('로그아웃했습니다.'), 'logout'));
        view.replaceChildren(state.page === 'resume' ? resumeView()
            : state.page === 'history' ? historyView() : interviewView());
    }
    lock(state.busy);
}

render();
