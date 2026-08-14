(() => {
	const app = document.querySelector('#project-members-app');
	if (!app) return;

	const base = SrrrgCommon.base;
	const { request, body, element, replaceChildren, setMessage } = SrrrgCommon;
	let projectId = new URLSearchParams(location.search).get('projectId');
	let loadSequence = 0;
	const byId = (id) => document.getElementById(id);
	const membersMessage = byId('members-message');
	const inviteMessage = byId('invite-message');

	function formatDate(value) {
		return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
	}

	function roleLabel(role) {
		return role === 'EDITOR' ? '링크 편집 가능' : role === 'VIEWER' ? '조회 전용' : '소유자';
	}

	function renderMembers(members) {
		const rows = members.map((member) => {
			const row = element('div', 'member-row');
			row.append(element('span', '', member.displayName || '이름 없음'), element('span', 'status-badge', roleLabel(member.role)));
			return row;
		});
		replaceChildren(byId('member-list'), rows);
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

	async function loadInvitations() {
		const response = await request(`${base}/api/web/projects/${projectId}/invitations`);
		if (!response.ok) return;
		renderInvitations(await response.json());
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

	byId('invite-form').addEventListener('submit', async (event) => {
		event.preventDefault();
		const submit = byId('invite-submit-button');
		const data = new FormData(event.target);
		submit.disabled = true;
		setMessage(inviteMessage, '초대 메일을 보내고 있습니다.');
		try {
			const response = await request(`${base}/api/web/projects/${projectId}/invitations`, {
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

	async function load(newProjectId = projectId) {
		projectId = newProjectId;
		const sequence = ++loadSequence;
		setMessage(membersMessage, '멤버를 불러오는 중입니다.');
		const projectResponse = await request(`${base}/api/web/projects/${projectId}`);
		if (sequence !== loadSequence) return;
		if (!projectResponse.ok) {
			setMessage(membersMessage, (await body(projectResponse)).message || '프로젝트를 불러올 수 없습니다.', true);
			return;
		}
		const project = await projectResponse.json();
		// 제목은 정적 sr-only 텍스트로 충분하다. 프로젝트 이름은 페이지 제목이 이미 말한다.
		byId('invitation-management').hidden = project.role !== 'OWNER';

		const membersResponse = await request(`${base}/api/web/projects/${projectId}/members`);
		if (sequence !== loadSequence) return;
		if (membersResponse.ok) renderMembers(await membersResponse.json());
		if (project.role === 'OWNER') await loadInvitations();
		setMessage(membersMessage, '');
	}

	// 멤버도 기본 탭이 아니므로 즉시 부르지 않는다. srrrg-projects.js 가 멤버 탭을
	// 처음 열 때 reload() 로 부른다 — 이전에는 여기서 한 번, 자동 선택 경로에서 또 한 번
	// 불러 같은 목록을 두 번 조회하기도 했다.
	window.SrrrgProjectMembers = { reload: load };
	if (!projectId) setMessage(membersMessage, '프로젝트를 불러오는 중입니다.');
})();
