(() => {
	const app = document.querySelector('#project-settings-app');
	if (!app) return;

	const base = SrrrgCommon.base;
	const { request, body, setMessage, submitting, confirmAction } = SrrrgCommon;
	const projectId = new URLSearchParams(location.search).get('projectId');
	const byId = (id) => document.getElementById(id);
	const settingsMessage = byId('settings-message');
	const domainMessage = byId('domain-change-message');
	let project = null;

	if (!projectId) {
		setMessage(settingsMessage, '프로젝트를 먼저 선택하세요.', true);
		return;
	}

	function applyRoleVisibility() {
		const isOwner = project.role === 'OWNER';
		byId('rename-project-form').closest('.project-card').hidden = !isOwner;
		byId('domain-title').closest('.project-card').hidden = !isOwner;
		byId('danger-zone-title').closest('.project-card').hidden = !isOwner;
		byId('import-section').hidden = project.role === 'VIEWER';
		// 조회 전용이면 모든 카드가 숨겨져 화면이 비어버리므로 이유를 알려준다.
		if (project.role === 'VIEWER') {
			setMessage(settingsMessage, '조회 전용 권한이라 변경할 수 있는 설정이 없습니다.');
		} else if (!isOwner) {
			// 편집자는 편입만 쓸 수 있다. 나머지 카드가 안 보이는 이유를 밝힌다.
			setMessage(settingsMessage, '이름·서브도메인·삭제는 프로젝트 소유자만 변경할 수 있습니다.');
		}
	}

	function renderProject() {
		byId('settings-project-name').textContent = `${project.name} · 설정`;
		byId('rename-project-name').value = project.name;
		byId('change-project-domain').value = project.subdomain || '';
		byId('project-subdomain-enabled').checked = project.subdomainEnabled;
		byId('project-subdomain-enabled').disabled = !project.subdomain;
		byId('release-project-subdomain').disabled = !project.subdomain;
		// 비활성 상태에는 이유를, 선점 상태에는 현재 값을 알려 준다.
		byId('subdomain-availability').textContent = project.subdomain
			? `현재 선점: ${project.subdomain}`
			: '서브도메인을 먼저 선점하면 활성화와 반납을 쓸 수 있습니다.';
		applyRoleVisibility();
	}

	async function loadProject() {
		const response = await request(`${base}/api/web/projects/${projectId}`);
		if (!response.ok) {
			setMessage(settingsMessage, (await body(response)).message || '프로젝트를 불러올 수 없습니다.', true);
			return false;
		}
		project = await response.json();
		renderProject();
		return true;
	}

	byId('rename-project-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(settingsMessage, '프로젝트 이름을 입력하세요.', true);
		await submitting(event.submitter, '저장 중...', async () => {
			setMessage(settingsMessage, '프로젝트 이름을 저장하고 있습니다...');
			const response = await request(`${base}/api/web/projects/${projectId}`, { method: 'PATCH', body: JSON.stringify({ name }) });
			const responseBody = await body(response);
			if (!response.ok) return setMessage(settingsMessage, responseBody.message || '프로젝트 이름을 변경할 수 없습니다.', true);
			setMessage(settingsMessage, '프로젝트 이름을 변경했습니다.');
			await loadProject();
		});
	});

	byId('change-project-domain-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const subdomain = new FormData(event.target).get('subdomain')?.trim().toLowerCase();
		if (!subdomain || !/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])$/.test(subdomain) || subdomain.length < 3 || subdomain.length > 63) {
			return setMessage(domainMessage, '서브도메인은 영문 소문자, 숫자, 하이픈을 사용한 3~63자로 입력하세요.', true);
		}
		if (subdomain === project.subdomain) return setMessage(domainMessage, '이미 선점한 서브도메인입니다.');
		await submitting(event.submitter, '선점 중...', async () => {
			setMessage(domainMessage, '서브도메인을 선점하고 있습니다...');
			const response = await request(`${base}/api/web/projects/${projectId}/subdomain`, { method: 'PUT', body: JSON.stringify({ subdomain }) });
			const responseBody = await body(response);
			if (!response.ok) return setMessage(domainMessage, responseBody.message || '서브도메인을 변경할 수 없습니다.', true);
			await loadProject();
			setMessage(domainMessage, '서브도메인을 선점했습니다. 활성화하면 신규 링크에 사용됩니다.');
		});
	});

	byId('project-subdomain-enabled').addEventListener('change', async (event) => {
		const enabled = event.target.checked;
		const response = await request(`${base}/api/web/projects/${projectId}/subdomain/activation`, { method: 'PATCH', body: JSON.stringify({ enabled }) });
		if (!response.ok) {
			event.target.checked = !enabled;
			return setMessage(domainMessage, (await body(response)).message || '활성화 상태를 변경할 수 없습니다.', true);
		}
		await loadProject();
		setMessage(domainMessage, enabled ? '신규 링크에 서브도메인을 사용합니다.' : '신규 링크에 기본 도메인을 사용합니다.');
	});

	byId('release-project-subdomain').addEventListener('click', async () => {
		if (!project.subdomain || !(await confirmAction({
			title: `"${project.subdomain}" 서브도메인을 반납할까요?`,
			body: '이미 만든 링크 주소는 그대로 동작합니다. 신규 링크에는 기본 도메인이 쓰이고, 반납한 서브도메인은 다른 프로젝트가 선점할 수 있습니다.',
			confirmLabel: '반납'
		}))) return;
		const response = await request(`${base}/api/web/projects/${projectId}/subdomain`, { method: 'DELETE' });
		if (!response.ok) return setMessage(domainMessage, (await body(response)).message || '서브도메인을 반납할 수 없습니다.', true);
		await loadProject();
		setMessage(domainMessage, '서브도메인을 반납했습니다.');
	});

	byId('import-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const data = new FormData(event.target);
		const code = data.get('code')?.trim();
		const secretKey = data.get('secretKey')?.trim();
		if (!code || !secretKey) return setMessage(settingsMessage, 'code와 secret key를 모두 입력하세요.', true);
		const form = event.target;
		await submitting(event.submitter, '편입 중...', async () => {
			setMessage(settingsMessage, '링크를 프로젝트로 편입하고 있습니다...');
			const response = await request(`${base}/api/web/projects/${projectId}/links/${encodeURIComponent(code)}/claim`, {
				method: 'POST', headers: { 'X-Srrrg-Secret-Key': secretKey }
			});
			if (!response.ok) return setMessage(settingsMessage, (await body(response)).message || '링크를 프로젝트로 편입할 수 없습니다.', true);
			form.reset();
			setMessage(settingsMessage, '링크를 프로젝트로 편입했습니다. 기존 secret key는 더 이상 사용할 수 없습니다.');
		});
	});

	byId('delete-project-button').addEventListener('click', async (event) => {
		// currentTarget 은 await 을 지나면 null 이 되므로 먼저 잡아둔다.
		const button = event.currentTarget;
		if (!(await confirmAction({
			title: `"${project.name}" 프로젝트를 삭제할까요?`,
			body: '이 프로젝트의 링크와 API key를 더 이상 쓸 수 없게 됩니다. 캠페인과 UTM 템플릿도 함께 정리됩니다.',
			confirmLabel: '프로젝트 삭제'
		}))) return;
		await submitting(button, '삭제 중...', async () => {
			const response = await request(`${base}/api/web/projects/${projectId}`, { method: 'DELETE' });
			if (!response.ok) return setMessage(settingsMessage, (await body(response)).message || '프로젝트를 삭제할 수 없습니다.', true);
			location.href = `${base}/projects`;
		});
	});

	loadProject();
})();
