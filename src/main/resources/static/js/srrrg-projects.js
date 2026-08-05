(() => {
  const base = document.querySelector('meta[name="context-path"]')?.content.replace(/\/$/, '') || '';
  const csrf = () => decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)?.[1] || '');
  const send = (url, options = {}) => fetch(url, {...options, headers: {'Content-Type':'application/json','X-XSRF-TOKEN':csrf(), ...(options.headers || {})}});
  const message = document.querySelector('#project-message');
  let selected;
  let refreshing;

  async function request(url, options = {}) {
    let response = await send(url, options);
    if (response.status !== 401) return response;
    if (!refreshing) refreshing = send(`${base}/api/web/auth/refresh`, {method:'POST'}).finally(() => refreshing = null);
    if (!(await refreshing).ok) {
      location.href = `${base}/login?returnTo=${encodeURIComponent('/projects')}`;
      return response;
    }
    return send(url, options);
  }

  async function members() {
    const memberResponse = await request(`${base}/api/web/projects/${selected.id}/members`);
    if (!memberResponse.ok) return;
    const rows = await memberResponse.json();
    document.querySelector('#member-list').innerHTML = rows.map(m => `<p>${m.displayName} · ${m.role}</p>`).join('');

    const invitationResponse = await request(`${base}/api/web/projects/${selected.id}/invitations`);
    if (!invitationResponse.ok) return;
    const invites = await invitationResponse.json();
    document.querySelector('#invitation-list').innerHTML = invites.map(x => `<p>${x.email} · ${x.role} <button data-cancel="${x.id}">취소</button> <button data-resend="${x.id}">재발송</button></p>`).join('');
    document.querySelectorAll('[data-cancel]').forEach(button => button.onclick = async () => {
      await request(`${base}/api/web/invitations/${button.dataset.cancel}`, {method:'DELETE'});
      members();
    });
    document.querySelectorAll('[data-resend]').forEach(button => button.onclick = async () => {
      await request(`${base}/api/web/invitations/${button.dataset.resend}/resend`, {method:'POST'});
      members();
    });
  }

  async function load() {
    const response = await request(`${base}/api/web/projects`);
    if (!response.ok) return;
    const items = await response.json();
    document.querySelector('#project-list').innerHTML = items.map(project => `<button class="secondary-button" data-id="${project.id}" data-name="${project.name}">${project.name} (${project.role})</button>`).join('');
    document.querySelectorAll('[data-id]').forEach(button => button.onclick = () => {
      selected = {id:button.dataset.id, name:button.dataset.name};
      document.querySelector('#project-name').textContent = selected.name;
      document.querySelector('#project-detail').hidden = false;
      members();
    });
  }

  document.querySelector('#create-project-form').onsubmit = async event => {
    event.preventDefault();
    const response = await request(`${base}/api/web/projects`, {method:'POST', body:JSON.stringify({name:new FormData(event.target).get('name')})});
    message.textContent = response.ok ? '프로젝트를 만들었습니다.' : '프로젝트를 만들 수 없습니다.';
    if (response.ok) load();
  };
  document.querySelector('#invite-form').onsubmit = async event => {
    event.preventDefault();
    const data = new FormData(event.target);
    const response = await request(`${base}/api/web/projects/${selected.id}/invitations`, {method:'POST', body:JSON.stringify({email:data.get('email'), role:data.get('role')})});
    message.textContent = response.ok ? '초대 메일을 보냈습니다.' : '초대 메일을 보낼 수 없습니다.';
    if (response.ok) { event.target.reset(); members(); }
  };
  document.querySelector('#import-form').onsubmit = async event => {
    event.preventDefault();
    const data = new FormData(event.target);
    const response = await request(`${base}/api/web/projects/${selected.id}/links/import`, {method:'POST', body:JSON.stringify({code:data.get('code'), secretKey:data.get('secretKey')})});
    message.textContent = response.ok ? '링크를 프로젝트로 편입했습니다. secret key는 더 이상 사용할 수 없습니다.' : '링크를 확인할 수 없습니다.';
  };
  load();
})();
