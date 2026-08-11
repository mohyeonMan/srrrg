(() => {
	const app = document.querySelector('#projects-app');
	if (!app) return;

	const base = SrrrgCommon.base;
	const { csrf, send, request, body, element, replaceChildren, setMessage } = SrrrgCommon;
	const params = new URLSearchParams(location.search);
	const selectedCampaignId = params.get('campaignId');
	const selectedLinkCode = params.get('linkCode');
	const activeView = params.get('view') || 'home';
	const showingTemplates = !selectedCampaignId && activeView === 'utm-templates';
	const showingSettings = !selectedCampaignId && activeView === 'project-settings';
	const state = { projects: [], selected: null, subdomain: null, subdomainEnabled: false, expiresOption: 'none',
		activityLinks: [], activityCampaigns: [], activityFilter: 'all', activitySort: 'recent' };
	const byId = (id) => document.getElementById(id);
	const projectMessage = byId('project-message');
	const createProjectMessage = byId('create-project-message');
	const linkMessage = byId('link-create-message');
	const originalUrlInput = byId('project-original-url');
	const expiresAtInput = byId('project-expires-at');
	const createLinkButton = byId('create-project-link-button');
	const activityList = byId('project-activity-list');
	const activityScrollbar = byId('project-activity-scrollbar');
	const activityScrollThumb = byId('project-activity-scroll-thumb');

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
		const projects = state.projects = await response.json();
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
		const options = projects.map((project) => {
			const option = element('button', 'project-picker-option', project.name);
			option.type = 'button';
			option.setAttribute('role', 'menuitem');
			option.dataset.projectId = project.id;
			option.addEventListener('click', () => location.href = `${base}/projects?projectId=${project.id}`);
			return option;
		});
		replaceChildren(byId('project-picker'), options);
	}

	async function selectProject(project) {
		state.selected = project;
		state.subdomain = project.subdomain;
		state.subdomainEnabled = project.subdomainEnabled;
		byId('project-empty').hidden = true;
		byId('project-detail').hidden = false;
		byId('project-heading-name').textContent = project.name;
		byId('project-heading-name').href = `${base}/projects?projectId=${project.id}`;
		byId('project-role').textContent = project.role;
		document.querySelectorAll('.project-picker-option').forEach((option) => {
			option.classList.toggle('is-active', option.dataset.projectId === String(project.id));
		});
		byId('project-create-link-nav').href = `${base}/projects?projectId=${project.id}&view=create-link`;
		byId('project-create-link-nav').classList.toggle('is-active', !selectedCampaignId && !selectedLinkCode && activeView === 'create-link');
		byId('project-create-campaign-nav').href = `${base}/projects?projectId=${project.id}&view=create-campaign`;
		byId('project-create-campaign-nav').classList.toggle('is-active', !selectedCampaignId && !selectedLinkCode && activeView === 'create-campaign');
		byId('project-templates-nav').href = `${base}/projects?projectId=${project.id}&view=utm-templates`;
		byId('project-templates-nav').classList.toggle('is-active', showingTemplates);
		byId('project-settings-nav').href = `${base}/projects?projectId=${project.id}&view=project-settings`;
		byId('project-settings-nav').classList.toggle('is-active', showingSettings);
		byId('project-domain').textContent = '도메인을 불러오는 중...';
		byId('copy-project-domain').disabled = true;
		byId('project-link-result').hidden = true;
		setMessage(linkMessage, '');
		setRoleVisibility(project.role);
		await loadProjectData();
		await loadCampaignTemplates();
		byId('project-detail').hidden = Boolean(selectedCampaignId) || Boolean(selectedLinkCode) || showingTemplates || showingSettings;
		showProjectPanel();
	}

	async function loadCampaignTemplates() {
		const response = await request(`${base}/api/web/projects/${state.selected.id}/utm-templates`);
		if (!response.ok) return;
		const templates = await response.json();
		const options = [element('option', '', '템플릿 없음')];
		options[0].value = '';
		for (const template of templates) {
			const option = element('option', '', template.name);
			option.value = String(template.id);
			options.push(option);
		}
		replaceChildren(byId('new-campaign-template'), options);
	}

	function setRoleVisibility(role) {
		const canEdit = role === 'OWNER' || role === 'EDITOR';
		state.canEdit = canEdit;
		byId('project-create-link-nav').hidden = !canEdit;
		byId('project-create-campaign-nav').hidden = !canEdit;
		byId('project-settings-nav').hidden = role !== 'OWNER';
		byId('create-campaign-form').hidden = !canEdit;
	}

	function showProjectPanel() {
		let panel = activeView === 'create-link' ? 'link' : activeView === 'create-campaign' ? 'campaign' : 'home';
		// 편집 권한이 없는 사람이 생성 화면 URL 로 들어오면(편집자가 공유한 링크 등)
		// 모든 패널이 숨겨져 빈 화면이 된다. 개요로 되돌린다.
		if (!state.canEdit && panel !== 'home') panel = 'home';
		byId('project-home-panel').hidden = panel !== 'home';
		if (byId('project-members-panel')) byId('project-members-panel').hidden = panel !== 'home';
		byId('link-create-panel').hidden = panel !== 'link' || !state.canEdit;
		byId('campaign-create-panel').hidden = panel !== 'campaign' || !state.canEdit;
		byId('project-create-link-nav').classList.toggle('is-active', panel === 'link');
		byId('project-create-campaign-nav').classList.toggle('is-active', panel === 'campaign');
	}

	async function loadProjectData() {
		if (!state.selected) return;
		const projectId = state.selected.id;
		const [overviewResponse, subdomainResponse] = await Promise.all([
			request(`${base}/api/web/projects/${projectId}/overview`),
			request(`${base}/api/web/projects/${projectId}/subdomain`)
		]);
		if (state.selected?.id !== projectId) return;

		if (subdomainResponse.ok) {
			const config = await subdomainResponse.json();
			state.subdomain = config.subdomain;
			state.subdomainEnabled = config.enabled;
			byId('project-domain').textContent = projectOrigin(config.enabled ? config.subdomain : null);
			byId('copy-project-domain').disabled = false;
		}
		if (overviewResponse.ok) {
			const overview = await overviewResponse.json();
			const links = overview.standaloneLinks || [];
			const campaigns = overview.campaigns || [];
			renderProjectItems(links, campaigns);
			byId('project-link-count').textContent = String(links.length);
			byId('campaign-count').textContent = String(campaigns.length);
		}
	}

	function projectOrigin(subdomain = state.subdomainEnabled ? state.subdomain : null) {
		const port = location.port ? `:${location.port}` : '';
		return `${location.protocol}//${subdomain ? `${subdomain}.` : ''}${location.hostname}${port}`;
	}

	function shortUrl(code, subdomain = state.subdomainEnabled ? state.subdomain : null) {
		return `${projectOrigin(subdomain)}/${encodeURIComponent(code)}`;
	}

	function renderProjectItems(links = state.activityLinks, campaigns = state.activityCampaigns) {
		state.activityLinks = links;
		state.activityCampaigns = campaigns;
		const items = [
			...links.map((link) => ({ type: 'link', createdAt: link.createdAt, value: link })),
			...campaigns.map((campaign) => ({ type: 'campaign', createdAt: campaign.createdAt, value: campaign }))
		].filter((item) => state.activityFilter === 'all' || item.type === state.activityFilter)
		.sort((left, right) => {
			if (state.activitySort === 'name') return (left.value.name || '이름없음').localeCompare(right.value.name || '이름없음', 'ko');
			const difference = new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
			return state.activitySort === 'oldest' ? -difference : difference;
		});
		if (!items.length) {
			replaceChildren(activityList, [element('p', 'project-activity-empty', '아직 만든 항목이 없습니다.')]);
			requestAnimationFrame(updateActivityScrollbar);
			return;
		}
		const nodes = items.map((item) => {
			const node = element('a', 'project-activity-item');
			const content = element('span', 'project-activity-content');
			if (item.type === 'campaign') {
				node.href = `${base}/projects?projectId=${state.selected.id}&campaignId=${item.value.id}`;
				node.classList.toggle('is-active', String(item.value.id) === selectedCampaignId);
				content.append(element('strong', '', item.value.name), element('small', '', `캠페인 · ${formatDate(item.createdAt)}`));
			} else {
				node.href = `${base}/projects?projectId=${state.selected.id}&linkCode=${encodeURIComponent(item.value.code)}`;
				node.classList.toggle('is-active', item.value.code === selectedLinkCode);
				content.append(element('strong', '', item.value.name || '이름없음'), element('small', '', `단일 링크 · ${formatDate(item.createdAt)}`));
			}
			node.append(element('span', `project-activity-kind ${item.type}`, item.type === 'campaign' ? 'C' : '↗'), content);
			return node;
		});
		replaceChildren(activityList, nodes);
		requestAnimationFrame(updateActivityScrollbar);
	}

	function updateActivityScrollbar() {
		const maxScroll = activityList.scrollHeight - activityList.clientHeight;
		activityScrollbar.hidden = maxScroll <= 0;
		if (maxScroll <= 0) return;
		const travel = Math.max(1, activityScrollbar.clientHeight - activityScrollThumb.offsetHeight);
		activityScrollThumb.style.transform = `translateY(${activityList.scrollTop / maxScroll * travel}px)`;
	}

	activityList.addEventListener('scroll', updateActivityScrollbar, { passive: true });
	new ResizeObserver(updateActivityScrollbar).observe(activityList);
	activityScrollbar.addEventListener('click', (event) => {
		if (event.target === activityScrollThumb) return;
		const bounds = activityScrollbar.getBoundingClientRect();
		const ratio = Math.max(0, Math.min(1, (event.clientY - bounds.top) / bounds.height));
		activityList.scrollTop = ratio * (activityList.scrollHeight - activityList.clientHeight);
	});
	activityScrollThumb.addEventListener('pointerdown', (event) => {
		const startY = event.clientY;
		const startScroll = activityList.scrollTop;
		const maxScroll = activityList.scrollHeight - activityList.clientHeight;
		const travel = Math.max(1, activityScrollbar.clientHeight - activityScrollThumb.offsetHeight);
		activityScrollThumb.setPointerCapture(event.pointerId);
		const move = (moveEvent) => activityList.scrollTop = startScroll + (moveEvent.clientY - startY) / travel * maxScroll;
		const stop = () => {
			activityScrollThumb.removeEventListener('pointermove', move);
			activityScrollThumb.removeEventListener('pointerup', stop);
			activityScrollThumb.removeEventListener('pointercancel', stop);
		};
		activityScrollThumb.addEventListener('pointermove', move);
		activityScrollThumb.addEventListener('pointerup', stop);
		activityScrollThumb.addEventListener('pointercancel', stop);
	});

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
				body: JSON.stringify({
					name: byId('project-link-name').value.trim() || null,
					originalUrl: originalUrlInput.value.trim(),
					expiresAt: expiresAt()
				})
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
		if (!name) return setMessage(createProjectMessage, '프로젝트 이름을 입력하세요.', true);
		const response = await request(`${base}/api/web/projects`, { method: 'POST', body: JSON.stringify({ name }) });
		const responseBody = await body(response);
		if (!response.ok) return setMessage(createProjectMessage, responseBody.message || '프로젝트를 만들 수 없습니다.', true);
		event.target.reset();
		byId('create-project-dialog').close();
		location.href = `${base}/projects?projectId=${responseBody.id}`;
	});

	byId('open-create-project').addEventListener('click', () => {
		setMessage(createProjectMessage, '');
		byId('create-project-dialog').showModal();
		byId('new-project-name').focus();
	});
	byId('close-create-project').addEventListener('click', () => byId('create-project-dialog').close());
	byId('project-activity-filter').addEventListener('click', (event) => {
		const filters = { all: ['campaign', '캠페인만'], campaign: ['link', '단일링크만'], link: ['all', '전체'] };
		[state.activityFilter, event.currentTarget.textContent] = filters[state.activityFilter];
		event.currentTarget.setAttribute('aria-label', `콘텐츠 종류: ${event.currentTarget.textContent}`);
		renderProjectItems();
	});
	byId('project-activity-sort').addEventListener('click', (event) => {
		const sorts = { recent: ['oldest', '오래된순'], oldest: ['name', '이름순'], name: ['recent', '최신순'] };
		[state.activitySort, event.currentTarget.textContent] = sorts[state.activitySort];
		event.currentTarget.setAttribute('aria-label', `콘텐츠 정렬: ${event.currentTarget.textContent}`);
		renderProjectItems();
	});
	byId('create-campaign-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		if (!state.selected) return;
		const data = new FormData(event.target);
		const name = data.get('name')?.trim();
		if (!name) return setMessage(byId('campaign-message'), '캠페인 이름을 입력하세요.', true);
		const defaultOriginalUrl = data.get('defaultOriginalUrl')?.trim() || null;
		const utmTemplateId = data.get('utmTemplateId') || null;
		const response = await request(`${base}/api/web/projects/${state.selected.id}/campaigns`, {
			method: 'POST', body: JSON.stringify({ name, defaultOriginalUrl })
		});
		const responseBody = await body(response);
		if (!response.ok) return setMessage(byId('campaign-message'), responseBody.message || '캠페인을 만들 수 없습니다.', true);
		if (utmTemplateId) {
			await request(`${base}/api/web/campaigns/${responseBody.id}/utm-template`, {
				method: 'PATCH', body: JSON.stringify({ utmTemplateId: Number(utmTemplateId) })
			});
		}
		event.target.reset();
		setMessage(byId('campaign-message'), '캠페인을 만들었습니다.');
		location.href = `${base}/projects?projectId=${state.selected.id}&campaignId=${responseBody.id}`;
	});

	// 모바일 레일 드로어. CSS 가 <768px 에서만 트리거를 노출하므로 여기선 상태만 토글합니다.
	const rail = byId('project-rail');
	const railToggle = byId('open-rail');
	const railScrim = byId('rail-scrim');
	function setRailOpen(open) {
		rail.dataset.open = String(open);
		railScrim.hidden = !open;
		railToggle.setAttribute('aria-expanded', String(open));
		if (open) rail.querySelector('a, button')?.focus();
		else railToggle.focus();
	}
	railToggle.addEventListener('click', () => setRailOpen(rail.dataset.open !== 'true'));
	byId('close-rail').addEventListener('click', () => setRailOpen(false));
	railScrim.addEventListener('click', () => setRailOpen(false));
	document.addEventListener('keydown', (event) => {
		if (event.key === 'Escape' && rail.dataset.open === 'true') setRailOpen(false);
	});

	selectExpiration('none');
	loadProjects(new URLSearchParams(location.search).get('projectId'));
})();
