(() => {
	const app = document.getElementById('account-app');
	if (!app) return;

	const form = document.getElementById('account-profile-form');
	const input = document.getElementById('account-display-name');
	const submit = form.querySelector('button[type="submit"]');
	const message = document.getElementById('account-message');
	const csrf = () => decodeURIComponent(document.cookie.split('; ')
		.find(cookie => cookie.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=') || '');
	const providerNames = { GOOGLE: 'Google', KAKAO: 'Kakao', GITHUB: 'GitHub' };

	const request = async (url, options = {}, retry = true) => {
		const response = await fetch(url, {
			...options,
			headers: { Accept: 'application/json', 'X-XSRF-TOKEN': csrf(), ...(options.headers || {}) }
		});
		if (response.status === 401 && retry) {
			const refreshed = await fetch(app.dataset.refreshUrl, {
				method: 'POST', headers: { 'X-XSRF-TOKEN': csrf() }
			});
			if (refreshed.ok) return request(url, options, false);
			window.location.assign(app.dataset.loginUrl);
			throw new Error('로그인이 필요합니다.');
		}
		if (!response.ok) throw new Error('요청을 처리하지 못했습니다.');
		return response.status === 204 ? null : response.json();
	};

	const render = account => {
		document.getElementById('account-email').textContent = account.email || '등록된 이메일 없음';
		document.getElementById('account-providers').textContent = account.providers
			.map(provider => providerNames[provider] || provider).join(', ') || '연결된 계정 없음';
		input.value = account.displayName;
		const headerName = document.querySelector('.srrrg-account-menu summary > span:last-child');
		if (headerName) headerName.textContent = account.displayName;
		input.disabled = false;
		submit.disabled = false;
	};

	request(app.dataset.accountUrl).then(render).catch(error => {
		message.textContent = error.message;
		message.classList.add('error');
	});

	form.addEventListener('submit', async event => {
		event.preventDefault();
		if (!input.reportValidity()) return;
		submit.disabled = true;
		message.classList.remove('error');
		message.textContent = '저장 중...';
		try {
			const account = await request(app.dataset.accountUrl, {
				method: 'PATCH',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ displayName: input.value })
			});
			render(account);
			message.textContent = '이름을 변경했습니다.';
		} catch (error) {
			message.textContent = error.message;
			message.classList.add('error');
		} finally {
			submit.disabled = false;
		}
	});

	const logout = async url => {
		await request(url, { method: 'POST' }).catch(() => null);
		window.location.assign(app.dataset.homeUrl);
	};
	document.getElementById('account-logout').addEventListener('click', () => logout(app.dataset.logoutUrl));
	document.getElementById('account-logout-all').addEventListener('click', () => logout(app.dataset.logoutAllUrl));
})();
