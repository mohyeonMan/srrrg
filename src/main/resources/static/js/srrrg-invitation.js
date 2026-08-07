(() => {
	const app = document.querySelector('#invitation-app');
	if (!app || app.dataset.available !== 'true') return;

	const base = document.querySelector('meta[name="context-path"]')?.content.replace(/\/$/, '') || '';
	const token = app.dataset.token;
	const acceptButton = document.querySelector('#accept-invitation');
	const actions = document.querySelector('#invitation-actions');
	const login = document.querySelector('#invitation-login');
	const message = document.querySelector('#invitation-message');

	const expiresAt = document.querySelector('#invitation-expires-at');
	if (expiresAt?.dateTime) {
		expiresAt.textContent = new Intl.DateTimeFormat('ko-KR', {
			dateStyle: 'long', timeStyle: 'short'
		}).format(new Date(expiresAt.dateTime));
	}

	function csrf() {
		return decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)?.[1] || '');
	}

	async function sendAccept() {
		return fetch(`${base}/api/web/invitations/${encodeURIComponent(token)}/accept`, {
			method: 'POST',
			headers: { Accept: 'application/json', 'X-XSRF-TOKEN': csrf() }
		});
	}

	async function refresh() {
		return fetch(`${base}/api/web/auth/refresh`, {
			method: 'POST', headers: { Accept: 'application/json', 'X-XSRF-TOKEN': csrf() }
		});
	}

	async function responseBody(response) {
		try {
			return await response.json();
		} catch (_) {
			return {};
		}
	}

	function showLogin() {
		actions.hidden = true;
		login.hidden = false;
		message.textContent = '로그인이 만료되었습니다. 다시 로그인하면 이 화면으로 돌아옵니다.';
		message.classList.add('error');
	}

	if (!acceptButton) return;
	acceptButton.addEventListener('click', async () => {
		acceptButton.disabled = true;
		acceptButton.dataset.loading = 'true';
		acceptButton.textContent = '수락 중...';
		message.textContent = '프로젝트 참여를 처리하고 있습니다...';
		message.classList.remove('error');

		try {
			let response = await sendAccept();
			if (response.status === 401) {
				if (!(await refresh()).ok) return showLogin();
				response = await sendAccept();
			}
			const body = await responseBody(response);
			if (!response.ok) {
				message.textContent = body.message || '초대가 만료되었거나 이미 사용되었습니다.';
				message.classList.add('error');
				return;
			}

			actions.hidden = true;
			document.querySelector('#invitation-result').hidden = false;
			const title = document.querySelector('#invitation-result-title');
			title.textContent = body.alreadyMember ? '이미 참여 중인 프로젝트입니다' : '프로젝트에 참여했습니다';
			document.querySelector('#open-invited-project').href = `${base}/projects?projectId=${encodeURIComponent(body.projectId)}`;
			message.textContent = '';
			title.focus();
		} catch (_) {
			message.textContent = '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.';
			message.classList.add('error');
		} finally {
			acceptButton.disabled = false;
			acceptButton.dataset.loading = 'false';
			acceptButton.textContent = '초대 수락';
		}
	});
})();
