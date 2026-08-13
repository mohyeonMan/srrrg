(() => {
	const app = document.querySelector('#projects-app');
	if (!app) return;

	const base = SrrrgCommon.base;
	const { csrf, send, request, requestShared, body, element, replaceChildren, setMessage, submitting } = SrrrgCommon;
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

	// srrrg-project-members.js 와 같은 표기를 쓴다. 배지에 OWNER 같은 enum 을 그대로 보여주지 않는다.
	function roleLabel(role) {
		return role === 'EDITOR' ? '링크 편집 가능' : role === 'VIEWER' ? '조회 전용' : '소유자';
	}

	async function selectProject(project) {
		state.selected = project;
		state.subdomain = project.subdomain;
		state.subdomainEnabled = project.subdomainEnabled;
		byId('project-empty').hidden = true;
		byId('project-detail').hidden = false;
		byId('project-heading-name').textContent = project.name;
		byId('project-heading-name').href = `${base}/projects?projectId=${project.id}`;
		byId('project-role').textContent = roleLabel(project.role);
		document.querySelectorAll('.project-picker-option').forEach((option) => {
			option.classList.toggle('is-active', option.dataset.projectId === String(project.id));
		});
		byId('project-templates-nav').href = `${base}/projects?projectId=${project.id}&view=utm-templates`;
		byId('project-templates-nav').classList.toggle('is-active', showingTemplates);
		byId('project-settings-nav').href = `${base}/projects?projectId=${project.id}&view=project-settings`;
		byId('project-settings-nav').classList.toggle('is-active', showingSettings);
		byId('project-domain').textContent = '불러오는 중';
		byId('project-link-result').hidden = true;
		setMessage(linkMessage, '');
		setRoleVisibility(project.role);
		await loadProjectData();
		// 템플릿 목록은 "새 캠페인" 폼의 select 를 채우는 용도라, 그 폼이 없는
		// 캠페인·링크·템플릿 뷰에서는 부를 필요가 없습니다(campaigns.js 가 따로 부릅니다).
		if (!selectedCampaignId && !selectedLinkCode && !showingTemplates) await loadCampaignTemplates();
		byId('project-detail').hidden = Boolean(selectedCampaignId) || Boolean(selectedLinkCode) || showingTemplates || showingSettings;
		// 통계 조각은 URL 파라미터만 읽으므로, projectId 없이 들어와 자동 선택된 경우
		// 선택된 프로젝트를 알려주지 않으면 "지정되지 않았습니다" 상태로 남는다.
		// 반대로 URL 에 projectId 가 있으면 통계 조각이 이미 그 값으로 불러왔으므로
		// 여기서 또 알려주면 같은 통계를 두 번 조회한다.
		if (!selectedCampaignId && !selectedLinkCode && !params.get('projectId')) {
			window.SrrrgStatistics?.reload(String(project.id), null);
			window.SrrrgProjectMembers?.reload(String(project.id));
		}
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
		byId('project-create-actions').hidden = !canEdit;
		// 설정 화면에는 편집자용 "기존 익명 링크 편입" 이 있는데 진입 경로가 소유자 전용이라
		// 편집자는 직접 URL 을 입력하는 방법 말고는 도달할 수 없었다.
		// 소유자 전용 카드는 srrrg-project-settings.js 가 계속 감춘다.
		byId('project-settings-nav').hidden = !canEdit;
	}

	// showProjectPanel() 은 사라졌다. 생성이 대화상자로 옮겨지면서
	// "개요 / 링크 생성 / 캠페인 생성" 세 상태를 오가며 요약·탭 바를 숨길 일이 없어졌고,
	// 남은 것은 템플릿에 이미 적힌 기본 상태와 탭 컨트롤러가 관리하는 패널 표시뿐이다.

	// 통계·멤버 탭. 묶음 자체는 SrrrgCommon.tabs 가 처리한다(캠페인 탭과 같은 구현).
	const PROJECT_TABS = ['statistics', 'members'];
	let projectTabs = null;

	function activeProjectTab() {
		return projectTabs ? projectTabs.active() : PROJECT_TABS[0];
	}

	// #project-view-tabs 는 캠페인·링크·설정·템플릿 뷰에서 렌더되지 않는다(projects.html 의 th:if).
	// 즉 이 묶음이 존재할 때는 캠페인 탭 묶음이 없으므로, ?tab= 을 그대로 읽어도 서로 섞이지 않는다.
	function bindProjectTabs() {
		if (!byId('project-view-tabs')) return;
		projectTabs = SrrrgCommon.tabs({
			list: PROJECT_TABS, prefix: 'project', param: 'tab', initial: params.get('tab')
		});
	}

	async function loadProjectData() {
		if (!state.selected) return;
		const projectId = state.selected.id;
		const [overviewResponse, subdomainResponse] = await Promise.all([
			request(`${base}/api/web/projects/${projectId}/overview`),
			// srrrg-campaigns.js 도 같은 값을 쓴다. 이 화면에서 바뀌지 않으므로 응답을 공유한다.
			requestShared(`${base}/api/web/projects/${projectId}/subdomain`)
		]);
		if (state.selected?.id !== projectId) return;

		if (subdomainResponse.ok) {
			const config = await subdomainResponse.json();
			state.subdomain = config.subdomain;
			state.subdomainEnabled = config.enabled;
			byId('project-domain').textContent = projectOrigin(config.enabled ? config.subdomain : null);
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
			replaceChildren(activityList, [element('p', 'project-activity-empty', '아직 캠페인이나 링크가 없습니다. 위 메뉴에서 만들어 보세요.')]);
			return;
		}
		const nodes = items.map((item) => {
			const node = element('a', 'project-activity-item');
			const content = element('span', 'project-activity-content');
			if (item.type === 'campaign') {
				node.href = `${base}/projects?projectId=${state.selected.id}&campaignId=${item.value.id}`;
				node.classList.toggle('is-active', String(item.value.id) === selectedCampaignId);
				content.append(element('strong', '', item.value.name), element('small', '', `캠페인 · ${formatDate(item.createdAt)}`));
				// 태블릿 폭에서는 아이콘만 보이므로 tooltip 으로 뜻을 알 수 있게 한다.
				node.title = `캠페인 · ${item.value.name}`;
			} else {
				node.href = `${base}/projects?projectId=${state.selected.id}&linkCode=${encodeURIComponent(item.value.code)}`;
				node.classList.toggle('is-active', item.value.code === selectedLinkCode);
				content.append(element('strong', '', item.value.name || '이름없음'), element('small', '', `단일 링크 · ${formatDate(item.createdAt)}`));
				node.title = `단일 링크 · ${item.value.name || '이름없음'}`;
			}
			node.append(element('span', `project-activity-kind ${item.type}`, item.type === 'campaign' ? 'C' : '↗'), content);
			return node;
		});
		replaceChildren(activityList, nodes);
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

	byId('create-project-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const form = event.target;
		const name = new FormData(form).get('name')?.trim();
		if (!name) return setMessage(createProjectMessage, '프로젝트 이름을 입력하세요.', true);
		await submitting(event.submitter, '만드는 중...', async () => {
			setMessage(createProjectMessage, '프로젝트를 만들고 있습니다...');
			const response = await request(`${base}/api/web/projects`, { method: 'POST', body: JSON.stringify({ name }) });
			const responseBody = await body(response);
			if (!response.ok) return setMessage(createProjectMessage, responseBody.message || '프로젝트를 만들 수 없습니다.', true);
			form.reset();
			byId('create-project-dialog').close();
			location.href = `${base}/projects?projectId=${responseBody.id}`;
		});
	});

	// 제목 옆 + 버튼과 빈 상태의 "첫 프로젝트 만들기" 가 같은 다이얼로그를 연다.
	function openCreateProject() {
		setMessage(createProjectMessage, '');
		byId('create-project-dialog').showModal();
		byId('new-project-name').focus();
	}

	byId('open-create-project').addEventListener('click', openCreateProject);
	byId('create-first-project').addEventListener('click', openCreateProject);
	byId('close-create-project').addEventListener('click', () => byId('create-project-dialog').close());

	// 링크·캠페인 생성. 열 때마다 지난 메시지와 결과를 지운다 —
	// 남겨 두면 두 번째로 열었을 때 이전 링크가 새로 만든 것처럼 보인다.
	byId('open-create-link').addEventListener('click', () => {
		setMessage(linkMessage, '');
		byId('project-link-result').hidden = true;
		byId('create-link-dialog').showModal();
		originalUrlInput.focus();
	});
	byId('close-create-link').addEventListener('click', () => byId('create-link-dialog').close());
	byId('open-create-campaign').addEventListener('click', () => {
		setMessage(byId('campaign-message'), '');
		byId('create-campaign-dialog').showModal();
		byId('new-campaign-name').focus();
	});
	byId('close-create-campaign').addEventListener('click', () => byId('create-campaign-dialog').close());
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
		const form = event.target;
		const data = new FormData(form);
		const name = data.get('name')?.trim();
		if (!name) return setMessage(byId('campaign-message'), '캠페인 이름을 입력하세요.', true);
		const defaultOriginalUrl = data.get('defaultOriginalUrl')?.trim() || null;
		const utmTemplateId = data.get('utmTemplateId') || null;
		await submitting(event.submitter, '만드는 중...', async () => {
			setMessage(byId('campaign-message'), '캠페인을 만들고 있습니다...');
			const response = await request(`${base}/api/web/projects/${state.selected.id}/campaigns`, {
				method: 'POST', body: JSON.stringify({ name, defaultOriginalUrl })
			});
			const responseBody = await body(response);
			if (!response.ok) return setMessage(byId('campaign-message'), responseBody.message || '캠페인을 만들 수 없습니다.', true);
			if (utmTemplateId) {
				const applied = await request(`${base}/api/web/campaigns/${responseBody.id}/utm-template`, {
					method: 'PATCH', body: JSON.stringify({ utmTemplateId: Number(utmTemplateId) })
				});
				if (!applied.ok) {
					setMessage(byId('campaign-message'),
						'캠페인은 만들었지만 UTM 템플릿을 연결하지 못했습니다. 캠페인 설정에서 다시 선택해 주세요.', true);
				}
			}
			form.reset();
			location.href = `${base}/projects?projectId=${state.selected.id}&campaignId=${responseBody.id}`;
		});
	});

	// 모바일 레일 드로어. CSS 가 <768px 에서만 트리거를 노출하므로 여기선 상태만 토글합니다.
	const rail = byId('project-rail');
	const railToggle = byId('open-rail');
	const railScrim = byId('rail-scrim');
	// 드로어가 열린 동안 본문은 스크림에 덮여 보이지 않는데도 Tab 으로 도달됐다.
	// 네이티브 inert 로 형제 콘텐츠를 통째로 빼면 포커스도 포인터도 함께 막힌다.
	// 비회원 생성 결과 패널에서 이미 쓰는 방식과 같다.
	// 레일은 main 안에 있으므로 body 자식만 걸러도 본문이 남는다. 덮이는 영역을 직접 지정한다.
	const coveredRegions = () => ['.srrrg-header', '.projects-heading', '#open-rail', '.project-workspace', '.srrrg-footer']
		.map((selector) => document.querySelector(selector))
		.filter(Boolean);

	function setRailOpen(open) {
		rail.dataset.open = String(open);
		railScrim.hidden = !open;
		railToggle.setAttribute('aria-expanded', String(open));
		coveredRegions().forEach((node) => node.inert = open);
		if (open) rail.querySelector('a, button')?.focus();
		else railToggle.focus();
	}
	railToggle.addEventListener('click', () => setRailOpen(rail.dataset.open !== 'true'));
	byId('close-rail').addEventListener('click', () => setRailOpen(false));
	railScrim.addEventListener('click', () => setRailOpen(false));
	document.addEventListener('keydown', (event) => {
		if (event.key === 'Escape' && rail.dataset.open === 'true') setRailOpen(false);
	});

	/**
	 * 레일에서 항목을 고르면 전체 페이지 이동이 일어나 포커스가 body 로 떨어진다.
	 * 키보드 사용자는 방금 고른 대상으로 가려고 Tab 을 처음부터 다시 밟아야 했다.
	 *
	 * "레일에서 왔는지" 를 저장해 두는 대신 URL 로 판단한다. 이 화면의 상태는 전부
	 * 쿼리파라미터에 있고, 특정 하위 화면을 지목하는 파라미터가 있다는 것 자체가
	 * 사용자가 그 화면을 열려고 했다는 뜻이다. 직접 링크와 새로고침에서도 똑같이 동작한다.
	 */
	function focusOpenedPanel() {
		const opensSpecificView = selectedCampaignId || selectedLinkCode || params.get('view');
		if (!opensSpecificView) return;
		// tabindex="-1" 이 이미 있는 제목들을 재사용한다.
		// 생성 대화상자의 제목(link-create-title, campaign-create-title)은 뺐다.
		// 대화상자는 항상 DOM 에 있으므로 여기 두면 알 수 없는 view 값에서
		// 숨어 있는 제목으로 포커스가 가고, 포커스 관리는 <dialog> 가 알아서 한다.
		const heading = byId('campaign-name') || byId('managed-link-title')
			|| byId('template-detail-name') || byId('settings-project-name');
		if (!heading) return;
		if (!heading.hasAttribute('tabindex')) heading.tabIndex = -1;
		heading.focus();
	}

	selectExpiration('none');
	bindProjectTabs();
	loadProjects(new URLSearchParams(location.search).get('projectId')).then(focusOpenedPanel);
})();
