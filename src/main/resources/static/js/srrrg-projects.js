(() => {
	const app = document.querySelector('#projects-app');
	if (!app) return;

	const base = document.querySelector('meta[name="context-path"]')?.content.replace(/\/$/, '') || '';
	const state = { selected: null, domain: null, expiresOption: 'none', refreshing: null };
	const byId = (id) => document.getElementById(id);
	const projectMessage = byId('project-message');
	const linkMessage = byId('link-create-message');
	const inviteMessage = byId('invite-message');
	const originalUrlInput = byId('project-original-url');
	const expiresAtInput = byId('project-expires-at');
	const createLinkButton = byId('create-project-link-button');

	function csrf() {
		return decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)?.[1] || '');
	}

	function send(url, options = {}) {
		const headers = { Accept: 'application/json', 'X-XSRF-TOKEN': csrf(), ...(options.headers || {}) };
		if (options.body) headers['Content-Type'] = 'application/json';
		return fetch(url, { ...options, headers });
	}

	async function request(url, options = {}) {
		let response = await send(url, options);
		if (response.status !== 401) return response;
		if (!state.refreshing) {
			state.refreshing = send(`${base}/api/web/auth/refresh`, { method: 'POST' })
				.finally(() => state.refreshing = null);
		}
		if (!(await state.refreshing).ok) {
			location.href = `${base}/login?returnTo=${encodeURIComponent(location.pathname)}`;
			return response;
		}
		return send(url, options);
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

	function setFieldError(input, error, text) {
		input.setAttribute('aria-invalid', 'true');
		error.textContent = text;
		error.hidden = false;
	}

	function clearFieldError(input, error) {
		input.removeAttribute('aria-invalid');
		error.textContent = '';
		error.hidden = true;
	}

	async function loadProjects(preferredId = state.selected?.id) {
		const response = await request(`${base}/api/web/projects`);
		if (!response.ok) {
			setMessage(projectMessage, (await body(response)).message || '프로젝트를 불러올 수 없습니다.', true);
			return;
		}
		const projects = await response.json();
		byId('project-count').textContent = String(projects.length);
		renderProjectList(projects);
		if (!projects.length) {
			state.selected = null;
			byId('project-empty').hidden = false;
			byId('project-detail').hidden = true;
			return;
		}
		await selectProject(projects.find((project) => String(project.id) === String(preferredId)) || projects[0]);
	}

	function renderProjectList(projects) {
		const buttons = projects.map((project) => {
			const button = element('button', 'project-list-button');
			button.type = 'button';
			button.dataset.projectId = project.id;
			button.setAttribute('aria-current', String(project.id === state.selected?.id));
			button.append(element('strong', '', project.name), element('span', '', `${project.slug} · ${project.role}`));
			button.addEventListener('click', () => selectProject(project));
			return button;
		});
		replaceChildren(byId('project-list'), buttons);
	}

	async function selectProject(project) {
		state.selected = project;
		state.domain = null;
		byId('project-empty').hidden = true;
		byId('project-detail').hidden = false;
		byId('project-name').textContent = project.name;
		byId('project-role').textContent = project.role;
		byId('rename-project-name').value = project.name;
		byId('project-domain').textContent = '도메인을 불러오는 중...';
		byId('copy-project-domain').disabled = true;
		byId('project-link-result').hidden = true;
		setMessage(linkMessage, '');
		setRoleVisibility(project.role);
		document.querySelectorAll('.project-list-button').forEach((button) => {
			button.setAttribute('aria-current', String(button.dataset.projectId === String(project.id)));
		});
		await loadProjectData();
	}

	function setRoleVisibility(role) {
		const canEdit = role === 'OWNER' || role === 'EDITOR';
		byId('link-create-panel').hidden = !canEdit;
		byId('import-section').hidden = !canEdit;
		byId('project-settings-section').hidden = role !== 'OWNER';
		byId('invitation-management').hidden = role !== 'OWNER';
		byId('campaign-section').hidden = false;
		byId('create-campaign-form').hidden = !canEdit;
	}

	async function loadProjectData() {
		if (!state.selected) return;
		const projectId = state.selected.id;
		const [overviewResponse, domainsResponse, membersResponse] = await Promise.all([
			request(`${base}/api/web/projects/${projectId}/overview`),
			request(`${base}/api/web/projects/${projectId}/domains`),
			request(`${base}/api/web/projects/${projectId}/members`)
		]);
		if (state.selected?.id !== projectId) return;

		if (domainsResponse.ok) {
			const domains = await domainsResponse.json();
			state.domain = domains[0]?.hostname || null;
			byId('project-domain').textContent = state.domain || '연결된 도메인이 없습니다.';
			byId('copy-project-domain').disabled = !state.domain;
		}
		if (overviewResponse.ok) {
			const overview = await overviewResponse.json();
			renderLinks(overview.standaloneLinks || []);
			renderCampaigns(overview.campaigns || []);
		}
		if (membersResponse.ok) renderMembers(await membersResponse.json());
		if (state.selected.role === 'OWNER') await loadInvitations(projectId);
	}

	function projectOrigin() {
		if (!state.domain) return '';
		const port = location.port ? `:${location.port}` : '';
		return `${location.protocol}//${state.domain}${port}`;
	}

	function shortUrl(code) {
		return `${projectOrigin()}/${encodeURIComponent(code)}`;
	}

	function renderLinks(links) {
		byId('project-link-count').textContent = String(links.length);
		if (!links.length) {
			replaceChildren(byId('project-link-list'), [element('p', 'project-empty-list', '아직 만든 프로젝트 링크가 없습니다.')]);
			return;
		}
		const rows = links.map((link) => {
			const row = element('article', 'project-link-item');
			const main = element('div', 'project-link-main');
			const anchor = element('a', 'project-link-short-url', state.domain ? shortUrl(link.code) : link.code);
			if (state.domain) {
				anchor.href = shortUrl(link.code);
				anchor.target = '_blank';
				anchor.rel = 'noopener noreferrer';
			}
			const original = element('p', 'project-link-original', link.originalUrl);
			original.title = link.originalUrl;
			main.append(anchor, original);
			const meta = element('div', 'project-link-meta');
			meta.append(
				element('span', '', link.expiresAt ? `만료 ${formatDate(link.expiresAt)}` : '만료 없음'),
				element('span', '', `진입 ${link.accessCount.toLocaleString()} · 이동 ${link.redirectCount.toLocaleString()}`)
			);
			row.append(main, meta);
			return row;
		});
		replaceChildren(byId('project-link-list'), rows);
	}

	function renderCampaigns(campaigns) {
		byId('campaign-count').textContent = String(campaigns.length);
		if (!campaigns.length) {
			replaceChildren(byId('campaign-list'), [element('p', 'project-empty-list', '아직 만든 캠페인이 없습니다.')]);
			return;
		}
		const rows = campaigns.map((campaign) => {
			const row = element('article', 'project-link-item');
			const main = element('div', 'project-link-main');
			const link = element('a', 'project-link-short-url', campaign.name);
			link.href = `${base}/campaigns?projectId=${state.selected.id}&campaignId=${campaign.id}`;
			main.append(link, element('p', 'project-link-original', campaign.description || '설명 없음'));
			const meta = element('div', 'project-link-meta');
			meta.append(element('span', '', campaign.utmTemplateName ? `템플릿 · ${campaign.utmTemplateName}` : 'UTM 템플릿 없음'));
			row.append(main, meta);
			return row;
		});
		replaceChildren(byId('campaign-list'), rows);
	}

	function renderMembers(members) {
		const rows = members.map((member) => {
			const row = element('div', 'member-row');
			row.append(element('span', '', member.displayName || '이름 없음'), element('span', 'status-badge', roleLabel(member.role)));
			return row;
		});
		replaceChildren(byId('member-list'), rows);
	}

	async function loadInvitations(projectId = state.selected?.id) {
		if (!projectId) return;
		const response = await request(`${base}/api/web/projects/${projectId}/invitations`);
		if (!response.ok || state.selected?.id !== projectId) return;
		renderInvitations(await response.json());
	}

	function renderInvitations(invitations) {
		byId('invitation-count').textContent = String(invitations.length);
		const rows = invitations.map((invitation) => {
			const row = element('div', 'invitation-row');
			const details = element('div', 'invitation-details');
			const expired = new Date(invitation.expiresAt).getTime() <= Date.now();
			details.append(
				element('strong', '', invitation.email),
				element('span', '', `${roleLabel(invitation.role)} · ${expired ? `${formatDate(invitation.expiresAt)} 만료` : `${formatDate(invitation.expiresAt)}까지`}`)
			);
			const actions = element('div', 'invitation-actions');
			const cancel = element('button', 'text-button', '취소');
			cancel.type = 'button';
			cancel.addEventListener('click', () => updateInvitation(invitation.id, 'DELETE', '초대를 취소했습니다.'));
			const resend = element('button', 'text-button', '재발송');
			resend.type = 'button';
			resend.addEventListener('click', () => updateInvitation(invitation.id, 'POST', '초대 메일을 다시 보냈습니다.'));
			actions.append(cancel, resend);
			row.append(details, actions);
			return row;
		});
		replaceChildren(byId('invitation-list'), rows.length ? rows : [element('p', 'help-text', '대기 중인 초대가 없습니다.')]);
	}

	async function updateInvitation(id, method, successMessage) {
		const suffix = method === 'POST' ? '/resend' : '';
		try {
			const response = await request(`${base}/api/web/invitations/${id}${suffix}`, { method });
			if (!response.ok) {
				setMessage(inviteMessage, (await body(response)).message || '초대를 변경할 수 없습니다.', true);
				return;
			}
			setMessage(inviteMessage, successMessage);
			await loadInvitations();
		} catch (_) {
			setMessage(inviteMessage, '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		}
	}

	function roleLabel(role) {
		return role === 'EDITOR' ? '링크 편집 가능' : role === 'VIEWER' ? '조회 전용' : '소유자';
	}

	function validateLinkForm() {
		const urlError = byId('project-url-error');
		const expirationError = byId('project-expiration-error');
		const value = originalUrlInput.value.trim();
		let valid = true;
		if (!value) {
			setFieldError(originalUrlInput, urlError, '단축할 URL을 입력하세요.');
			valid = false;
		} else if (value.length > 2048) {
			setFieldError(originalUrlInput, urlError, 'URL은 최대 2,048자까지 입력할 수 있습니다.');
			valid = false;
		} else if (!isHttpUrl(value)) {
			setFieldError(originalUrlInput, urlError, 'http 또는 https로 시작하는 올바른 URL을 입력하세요.');
			valid = false;
		} else {
			clearFieldError(originalUrlInput, urlError);
		}

		if (state.expiresOption === 'custom' && (!expiresAtInput.value || new Date(expiresAtInput.value).getTime() <= Date.now())) {
			setFieldError(expiresAtInput, expirationError, '만료 시각은 현재보다 이후여야 합니다.');
			valid = false;
		} else {
			clearFieldError(expiresAtInput, expirationError);
		}
		return valid;
	}

	function isHttpUrl(value) {
		try {
			const url = new URL(value);
			return (url.protocol === 'http:' || url.protocol === 'https:') && Boolean(url.hostname);
		} catch (_) {
			return false;
		}
	}

	function expiresAt() {
		if (state.expiresOption === 'none') return null;
		if (state.expiresOption === 'custom') return new Date(expiresAtInput.value).toISOString();
		const [amount, unit] = state.expiresOption.split(':');
		const value = new Date();
		const count = Number(amount);
		if (unit === 'hours') value.setHours(value.getHours() + count);
		if (unit === 'days') value.setDate(value.getDate() + count);
		if (unit === 'months') value.setMonth(value.getMonth() + count);
		if (unit === 'years') value.setFullYear(value.getFullYear() + count);
		return value.toISOString();
	}

	function selectExpiration(option) {
		state.expiresOption = option;
		document.querySelectorAll('[data-expires-option]').forEach((button) => {
			button.setAttribute('aria-pressed', String(button.dataset.expiresOption === option));
		});
		byId('project-custom-expiration-field').hidden = option !== 'custom';
		if (option === 'custom' && !expiresAtInput.value) {
			const value = new Date(Date.now() + 60 * 60 * 1000);
			expiresAtInput.value = localDateTime(value);
			expiresAtInput.focus();
		}
		updateCreateButton();
	}

	function localDateTime(date) {
		const offset = date.getTimezoneOffset() * 60_000;
		return new Date(date.getTime() - offset).toISOString().slice(0, 16);
	}

	function updateCreateButton() {
		if (createLinkButton.dataset.loading === 'true') return;
		const invalidExpiration = state.expiresOption === 'custom'
			&& (!expiresAtInput.value || new Date(expiresAtInput.value).getTime() <= Date.now());
		createLinkButton.disabled = !originalUrlInput.value.trim() || invalidExpiration;
	}

	function setCreateLoading(loading) {
		createLinkButton.dataset.loading = String(loading);
		createLinkButton.disabled = loading;
		createLinkButton.textContent = loading ? '생성 중...' : '단축 URL 만들기';
	}

	function formatDate(value) {
		return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
	}

	async function copy(text, button, successMessage = '복사했습니다.') {
		try {
			await navigator.clipboard.writeText(text);
			const label = button.textContent;
			button.textContent = '복사됨';
			setMessage(projectMessage, successMessage);
			setTimeout(() => button.textContent = label, 1500);
		} catch (_) {
			setMessage(projectMessage, '클립보드에 복사할 수 없습니다.', true);
		}
	}

	document.querySelectorAll('[data-expires-option]').forEach((button) => {
		button.addEventListener('click', () => selectExpiration(button.dataset.expiresOption));
	});

	originalUrlInput.addEventListener('input', () => {
		clearFieldError(originalUrlInput, byId('project-url-error'));
		setMessage(linkMessage, '');
		updateCreateButton();
	});
	expiresAtInput.addEventListener('input', () => {
		clearFieldError(expiresAtInput, byId('project-expiration-error'));
		updateCreateButton();
	});

	byId('create-project-link-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		if (!state.selected || !validateLinkForm()) return;
		setCreateLoading(true);
		setMessage(linkMessage, '단축 URL을 만들고 있습니다...');
		try {
			const response = await request(`${base}/api/web/projects/${state.selected.id}/links`, {
				method: 'POST',
				body: JSON.stringify({ originalUrl: originalUrlInput.value.trim(), expiresAt: expiresAt() })
			});
			const responseBody = await body(response);
			if (!response.ok) {
				setMessage(linkMessage, responseBody.message || '단축 URL을 만들 수 없습니다.', true);
				return;
			}
			const url = shortUrl(responseBody.code);
			const link = byId('created-project-link');
			link.textContent = url;
			link.href = url;
			byId('project-link-result').hidden = false;
			byId('project-link-result-title').focus();
			setMessage(linkMessage, '프로젝트 링크를 만들었습니다.');
			event.target.reset();
			selectExpiration('none');
			await loadProjectData();
		} catch (_) {
			setMessage(linkMessage, '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			setCreateLoading(false);
			updateCreateButton();
		}
	});

	byId('copy-created-project-link').addEventListener('click', () => {
		copy(byId('created-project-link').textContent, byId('copy-created-project-link'), '단축 URL을 복사했습니다.');
	});
	byId('copy-project-domain').addEventListener('click', () => {
		copy(projectOrigin(), byId('copy-project-domain'), '프로젝트 도메인을 복사했습니다.');
	});

	byId('create-project-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(projectMessage, '프로젝트 이름을 입력하세요.', true);
		const response = await request(`${base}/api/web/projects`, { method: 'POST', body: JSON.stringify({ name }) });
		const responseBody = await body(response);
		if (!response.ok) return setMessage(projectMessage, responseBody.message || '프로젝트를 만들 수 없습니다.', true);
		event.target.reset();
		setMessage(projectMessage, '프로젝트를 만들었습니다.');
		await loadProjects(responseBody.id);
	});

	byId('create-campaign-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		if (!state.selected) return;
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(byId('campaign-message'), '캠페인 이름을 입력하세요.', true);
		const response = await request(`${base}/api/web/projects/${state.selected.id}/campaigns`, { method: 'POST', body: JSON.stringify({ name }) });
		const responseBody = await body(response);
		if (!response.ok) return setMessage(byId('campaign-message'), responseBody.message || '캠페인을 만들 수 없습니다.', true);
		event.target.reset();
		setMessage(byId('campaign-message'), '캠페인을 만들었습니다.');
		location.href = `${base}/campaigns?projectId=${state.selected.id}&campaignId=${responseBody.id}`;
	});

	byId('rename-project-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		if (!state.selected) return;
		const name = new FormData(event.target).get('name')?.trim();
		if (!name) return setMessage(projectMessage, '프로젝트 이름을 입력하세요.', true);
		const response = await request(`${base}/api/web/projects/${state.selected.id}`, { method: 'PATCH', body: JSON.stringify({ name }) });
		const responseBody = await body(response);
		if (!response.ok) return setMessage(projectMessage, responseBody.message || '프로젝트 이름을 변경할 수 없습니다.', true);
		setMessage(projectMessage, '프로젝트 이름을 변경했습니다.');
		await loadProjects(responseBody.id);
	});

	byId('delete-project-button').addEventListener('click', async () => {
		if (!state.selected || !confirm(`“${state.selected.name}” 프로젝트를 삭제할까요? 프로젝트 링크와 API key를 더 이상 사용할 수 없습니다.`)) return;
		const response = await request(`${base}/api/web/projects/${state.selected.id}`, { method: 'DELETE' });
		if (!response.ok) return setMessage(projectMessage, (await body(response)).message || '프로젝트를 삭제할 수 없습니다.', true);
		state.selected = null;
		setMessage(projectMessage, '프로젝트를 삭제했습니다.');
		await loadProjects();
	});

	byId('invite-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		if (!state.selected) return;
		const submit = byId('invite-submit-button');
		const data = new FormData(event.target);
		submit.disabled = true;
		setMessage(inviteMessage, '초대 메일을 보내고 있습니다.');
		try {
			const response = await request(`${base}/api/web/projects/${state.selected.id}/invitations`, {
				method: 'POST', body: JSON.stringify({ email: data.get('email'), role: data.get('role') })
			});
			if (!response.ok) return setMessage(inviteMessage, (await body(response)).message || '초대 메일을 보낼 수 없습니다.', true);
			event.target.reset();
			setMessage(inviteMessage, '초대 메일을 보냈습니다.');
			await loadInvitations();
		} catch (_) {
			setMessage(inviteMessage, '서버에 연결할 수 없습니다. 잠시 후 다시 시도하세요.', true);
		} finally {
			submit.disabled = false;
		}
	});

	byId('import-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		if (!state.selected) return;
		const data = new FormData(event.target);
		const code = data.get('code')?.trim();
		const secretKey = data.get('secretKey')?.trim();
		if (!code || !secretKey) return setMessage(projectMessage, 'code와 secret key를 모두 입력하세요.', true);
		const response = await request(`${base}/api/web/projects/${state.selected.id}/links/${encodeURIComponent(code)}/claim`, {
			method: 'POST', headers: { 'X-Srrrg-Secret-Key': secretKey }
		});
		if (!response.ok) return setMessage(projectMessage, (await body(response)).message || '링크를 프로젝트로 편입할 수 없습니다.', true);
		event.target.reset();
		setMessage(projectMessage, '링크를 프로젝트로 편입했습니다. 기존 secret key는 더 이상 사용할 수 없습니다.');
		await loadProjectData();
	});

	selectExpiration('none');
	loadProjects(new URLSearchParams(location.search).get('projectId'));
})();
