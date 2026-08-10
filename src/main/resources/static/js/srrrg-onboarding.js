(() => {
	const app = document.getElementById('onboarding-app');
	if (!app) return;

	const form = document.getElementById('onboarding-form');
	const name = document.getElementById('onboarding-name');
	const email = document.getElementById('onboarding-email');
	const help = document.getElementById('onboarding-email-help');
	const submit = form.querySelector('button[type="submit"]');
	const message = document.getElementById('onboarding-message');
	const csrf = () => decodeURIComponent(document.cookie.split('; ')
		.find(cookie => cookie.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=') || '');
	const returnTo = () => {
		const path = new URLSearchParams(location.search).get('returnTo') || app.dataset.homeUrl;
		return path.startsWith('/') && !path.startsWith('//') ? path : app.dataset.homeUrl;
	};

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
			window.location.assign(`${app.dataset.loginUrl}?returnTo=${encodeURIComponent(returnTo())}`);
			throw new Error('로그인이 필요합니다.');
		}
		if (!response.ok) throw new Error('정보를 저장하지 못했습니다.');
		return response.json();
	};

	request(app.dataset.accountUrl).then(account => {
		name.value = account.displayName;
		email.value = account.email || '';
		email.readOnly = Boolean(account.email);
		help.textContent = account.email
			? '로그인 계정에서 확인된 이메일입니다.'
			: '로그인 계정에서 이메일을 받지 못해 직접 입력이 필요합니다.';
		name.disabled = false;
		email.disabled = false;
		submit.disabled = false;
	}).catch(error => {
		message.textContent = error.message;
		message.classList.add('error');
	});

	form.addEventListener('submit', async event => {
		event.preventDefault();
		if (!form.reportValidity()) return;
		submit.disabled = true;
		message.classList.remove('error');
		message.textContent = '저장 중...';
		try {
			await request(app.dataset.completeUrl, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ displayName: name.value, email: email.value })
			});
			window.location.assign(returnTo());
		} catch (error) {
			message.textContent = error.message;
			message.classList.add('error');
			submit.disabled = false;
		}
	});
})();
