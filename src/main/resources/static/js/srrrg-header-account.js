(() => {
	const button = document.getElementById('header-logout-button');
	if (!button) return;

	const csrf = () => decodeURIComponent(document.cookie.split('; ')
		.find(cookie => cookie.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=') || '');

	button.addEventListener('click', async () => {
		button.disabled = true;
		try {
			await fetch(button.dataset.logoutUrl, {
				method: 'POST',
				headers: { 'X-XSRF-TOKEN': csrf() }
			});
		} finally {
			window.location.assign(button.dataset.homeUrl);
		}
	});
})();
