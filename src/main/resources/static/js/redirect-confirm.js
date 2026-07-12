(function () {
	const toggleButton = document.getElementById('original-url-toggle');
	const summary = document.getElementById('original-url-summary');
	const full = document.getElementById('original-url-full');
	const backButton = document.getElementById('go-back-button');

	if (toggleButton && summary && full) {
		toggleButton.addEventListener('click', () => {
			const nextFullVisible = full.hidden;
			full.hidden = !nextFullVisible;
			summary.hidden = nextFullVisible;
			toggleButton.setAttribute('aria-expanded', String(nextFullVisible));
			toggleButton.textContent = nextFullVisible ? '접기' : '전체 URL 보기';
		});
	}

	if (backButton) {
		backButton.addEventListener('click', () => {
			if (document.referrer && window.history.length > 1) {
				window.history.back();
				return;
			}
			window.location.href = backButton.dataset.fallbackUrl || '/';
		});
	}
})();
