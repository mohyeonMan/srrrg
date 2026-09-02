package link.srrrg.project;

/**
 * 프로젝트 권한 등급. 선언 순서가 곧 권한 서열이며, {@code ProjectService.requireRole}이
 * {@code ordinal()}로 비교한다. 앞쪽일수록 넓은 권한이므로 순서를 바꾸거나 중간에 값을 끼워 넣으면
 * 모든 권한 판정이 조용히 달라진다. 새 등급은 뒤에 추가한다.
 */
public enum ProjectRole {
	OWNER, EDITOR, VIEWER
}
