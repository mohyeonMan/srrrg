(() => {
	const projectId = new URLSearchParams(location.search).get('projectId');
	if (!projectId) return;
	document.querySelectorAll('[data-preserve-project]').forEach((link) => {
		const url = new URL(link.href, location.origin);
		if (!url.searchParams.has('projectId')) url.searchParams.set('projectId', projectId);
		link.href = url.toString();
	});
})();
