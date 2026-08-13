(() => {
	const base = document.querySelector('meta[name="context-path"]')?.content.replace(/\/$/, '') || '';
	let refreshing = null;

	function csrf() {
		return decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)?.[1] || '');
	}

	function send(url, options = {}) {
		const headers = { Accept: 'application/json', 'X-XSRF-TOKEN': csrf(), ...(options.headers || {}) };
		if (options.body && !(options.body instanceof FormData) && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
		return fetch(url, { ...options, headers });
	}

	async function request(url, options = {}) {
		let response = await send(url, options);
		if (response.status !== 401) return response;
		if (!refreshing) {
			refreshing = send(`${base}/api/web/auth/refresh`, { method: 'POST' }).finally(() => refreshing = null);
		}
		if (!(await refreshing).ok) {
			location.href = `${base}/login?returnTo=${encodeURIComponent(location.pathname + location.search)}`;
			return response;
		}
		return send(url, options);
	}

	/**
	 * 한 페이지 안에서 여러 스크립트가 같은 읽기 전용 값을 각각 받아오는 것을 막는다.
	 * 진행 중 요청만 합치는 방식은 두 호출이 시간상 겹치지 않으면 소용이 없어서,
	 * 호출한 쪽이 "이 값은 이 페이지에서 안 바뀐다"고 판단한 URL 만 명시적으로 넘긴다.
	 * 값이 바뀔 수 있는 조회에는 쓰지 않는다.
	 */
	const shared = new Map();

	function requestShared(url, options = {}) {
		if (!shared.has(url)) shared.set(url, request(url, options));
		return shared.get(url).then((response) => response.clone());
	}

	async function body(response) {
		try {
			return await response.json();
		} catch (_) {
			return {};
		}
	}

	function element(tag, className, text) {
		const node = document.createElement(tag);
		if (className) node.className = className;
		if (text !== undefined) node.textContent = text;
		return node;
	}

	function replaceChildren(target, children) {
		target.replaceChildren(...children);
	}

	function setMessage(target, text, error = false) {
		target.textContent = text;
		target.classList.toggle('error', error);
	}

	/**
	 * 제출 중에는 버튼을 잠그고 라벨을 처리 중 문구로 바꾼 뒤 원래대로 되돌린다.
	 * 이미 처리 중이면 아무것도 하지 않으므로 더블클릭으로 중복 생성되지 않는다.
	 */
	async function submitting(button, pendingLabel, run) {
		if (button.dataset.pending === 'true') return;
		const label = button.textContent;
		button.dataset.pending = 'true';
		button.disabled = true;
		button.textContent = pendingLabel;
		try {
			return await run();
		} finally {
			delete button.dataset.pending;
			button.disabled = false;
			button.textContent = label;
		}
	}

	/**
	 * 되돌리기 어려운 동작의 확인을 네이티브 <dialog> 로 받는다.
	 * 브라우저 기본 confirm() 은 스타일을 맞출 수 없고 사용자가 억제할 수도 있어 쓰지 않는다.
	 * 대화상자가 없는 화면에서는 진행을 막지 않도록 true 로 처리한다.
	 */
	// 열려 있는 확인 대화상자의 결과를 확정하는 함수. 닫히면 null 로 돌아간다.
	// dialog 의 close 이벤트에 의존하지 않는다. 일부 실행 환경에서 이 이벤트가 발생하지 않아
	// 사용자가 실행을 눌러도 아무 일도 일어나지 않는 상태가 됐다.
	let settleConfirm = null;

	function confirmAction({ title, body: bodyText, confirmLabel = '삭제', danger = true }) {
		const dialog = document.getElementById('confirm-dialog');
		if (!dialog) return Promise.resolve(true);
		settleConfirm?.(false);
		document.getElementById('confirm-dialog-title').textContent = title;
		document.getElementById('confirm-dialog-body').textContent = bodyText;
		const accept = document.getElementById('confirm-dialog-accept');
		accept.textContent = confirmLabel;
		accept.className = danger ? 'danger-button' : 'primary-button';
		dialog.showModal();
		document.getElementById('confirm-dialog-cancel').focus();
		return new Promise((resolve) => {
			settleConfirm = (accepted) => {
				settleConfirm = null;
				if (dialog.open) dialog.close();
				resolve(accepted);
			};
		});
	}

	document.addEventListener('click', (event) => {
		if (!settleConfirm) return;
		if (event.target.id === 'confirm-dialog-accept') settleConfirm(true);
		else if (event.target.id === 'confirm-dialog-cancel') settleConfirm(false);
	});

	document.addEventListener('keydown', (event) => {
		if (event.key === 'Escape' && settleConfirm) settleConfirm(false);
	});

	/* 탭 묶음. srrrg-campaigns.js 와 srrrg-projects.js 가 같은 코드를 각자 갖고 있었다 —
	   `{prefix}-tab-{name}` / `{prefix}-panel-{name}`, roving tabindex, 좌우 순환 방향키까지 동일.

	   srrrg-management.js 의 탭은 여기 넣지 않는다. ID 규칙이 다르고(analytics-tab),
	   비활성 탭을 건너뛰며, 저장 안 된 변경이 있으면 확인 대화상자를 띄우는
	   requestTab 게이트가 있다. 그 화면은 secret key 게이트 뒤라 주소를 공유해도
	   열리지 않으므로 URL 동기화의 이득도 없다. 억지로 합치면 옵션만 늘고 얻는 게 없다.

	   URL 은 사용자가 탭을 누를 때만 쓴다. 초기 bind 에서 쓰지 않는 이유:
	   #project-view-tabs 는 캠페인을 보는 중에도 hidden 으로 남아 있어 두 묶음이 함께
	   초기화된다. 초기화 때 URL 을 쓰면 서로를 덮어써 엉뚱한 값이 남는다.
	   보이지 않는 묶음은 클릭될 수 없으므로, 쓰기를 클릭으로 한정하면 경합이 사라진다. */
	function tabs({ list, prefix, param, initial }) {
		let active = list.includes(initial) ? initial : list[0];

		function paint() {
			list.forEach((name) => {
				const button = document.getElementById(`${prefix}-tab-${name}`);
				const panel = document.getElementById(`${prefix}-panel-${name}`);
				if (!button || !panel) return;
				const isActive = name === active;
				button.setAttribute('aria-selected', String(isActive));
				button.tabIndex = isActive ? 0 : -1;
				panel.hidden = !isActive;
			});
		}

		function switchTo(name, fromUser = false) {
			if (!list.includes(name)) return;
			active = name;
			paint();
			if (!fromUser || !param) return;
			const url = new URL(location.href);
			url.searchParams.set(param, name);
			history.replaceState(null, '', url);
		}

		list.forEach((name) => {
			const button = document.getElementById(`${prefix}-tab-${name}`);
			if (!button) return;
			button.addEventListener('click', () => switchTo(name, true));
			button.addEventListener('keydown', (event) => {
				const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
				if (!step) return;
				event.preventDefault();
				const next = list[(list.indexOf(name) + step + list.length) % list.length];
				document.getElementById(`${prefix}-tab-${next}`)?.focus();
				switchTo(next, true);
			});
		});
		paint();

		return { switchTo, active: () => active };
	}

	window.SrrrgCommon = {
		base, csrf, send, request, requestShared, body, element, replaceChildren, setMessage, submitting, confirmAction, tabs
	};
})();
