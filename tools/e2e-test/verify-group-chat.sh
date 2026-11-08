#!/usr/bin/env bash

set -euo pipefail

ADMIN_ID=${ADMIN_ID:-UserAccount-01H42K4ABWQ5V2XQEP3A48VE0Z}
USER_ACCOUNT_ID=${USER_ACCOUNT_ID:-UserAccount-01H7C6DWMK1BKS1JYH1XZE529M}
WRITE_API_SERVER_BASE_URL=${WRITE_API_SERVER_BASE_URL:-http://localhost:58080/graphql}
READ_API_SERVER_BASE_URL=${READ_API_SERVER_BASE_URL:-http://localhost:58082/graphql}
POLL_TIMEOUT=${POLL_TIMEOUT:-60}
POLL_INTERVAL=${POLL_INTERVAL:-1}
REQUEST_TIMEOUT=${REQUEST_TIMEOUT:-10}

CREATE_GROUP_CHAT='mutation CreateGroupChat($input: CreateGroupChatInput!) { createGroupChat(input: $input) { groupChatId } }'
ADD_MEMBER='mutation AddMember($input: AddMemberInput!) { addMember(input: $input) { groupChatId } }'
POST_MESSAGE='mutation PostMessage($input: PostMessageInput!) { postMessage(input: $input) { groupChatId messageId } }'
RENAME_GROUP_CHAT='mutation RenameGroupChat($input: RenameGroupChatInput!) { renameGroupChat(input: $input) { groupChatId } }'
EDIT_MESSAGE='mutation EditMessage($input: EditMessageInput!) { editMessage(input: $input) { groupChatId } }'
DELETE_MESSAGE='mutation DeleteMessage($input: DeleteMessageInput!) { deleteMessage(input: $input) { groupChatId } }'
REMOVE_MEMBER='mutation RemoveMember($input: RemoveMemberInput!) { removeMember(input: $input) { groupChatId } }'
DELETE_GROUP_CHAT='mutation DeleteGroupChat($input: DeleteGroupChatInput!) { deleteGroupChat(input: $input) { groupChatId } }'

GET_GROUP_CHAT='query GetGroupChat($groupChatId: String!, $userAccountId: String!) { getGroupChat(groupChatId: $groupChatId, userAccountId: $userAccountId) { id name ownerId createdAt updatedAt } }'
GET_GROUP_CHATS='query GetGroupChats($userAccountId: String!) { getGroupChats(userAccountId: $userAccountId) { id name ownerId } }'
GET_MEMBER='query GetMember($groupChatId: String!, $userAccountId: String!) { getMember(groupChatId: $groupChatId, userAccountId: $userAccountId) { id groupChatId userAccountId role } }'
GET_MEMBERS='query GetMembers($groupChatId: String!, $userAccountId: String!) { getMembers(groupChatId: $groupChatId, userAccountId: $userAccountId) { id groupChatId userAccountId role } }'
GET_MESSAGE='query GetMessage($messageId: String!, $userAccountId: String!) { getMessage(messageId: $messageId, userAccountId: $userAccountId) { id groupChatId text } }'
GET_MESSAGES='query GetMessages($groupChatId: String!, $userAccountId: String!) { getMessages(groupChatId: $groupChatId, userAccountId: $userAccountId) { id groupChatId text } }'

graphql() {
	local url=$1
	local query=$2
	local variables=${3:-'{}'}
	local timeout=${4:-$REQUEST_TIMEOUT}
	jq -cn --arg query "$query" --argjson variables "$variables" '{query: $query, variables: $variables}' |
		curl -sS --fail-with-body --connect-timeout "$timeout" --max-time "$timeout" \
			-X POST -H "Content-Type: application/json" "$url" --data-binary @-
}

request_timeout_for_deadline() {
	local deadline=$1
	local remaining=$((deadline - SECONDS))
	if [ "$remaining" -lt 1 ]; then
		remaining=1
	fi
	if [ "$remaining" -lt "$REQUEST_TIMEOUT" ]; then
		printf '%s' "$remaining"
	else
		printf '%s' "$REQUEST_TIMEOUT"
	fi
}

wait_for_graphql() {
	local name=$1
	local url=$2
	local response=""
	local deadline=$((SECONDS + POLL_TIMEOUT))
	while [ "$SECONDS" -lt "$deadline" ]; do
		local request_timeout
		request_timeout=$(request_timeout_for_deadline "$deadline")
		if response=$(graphql "$url" '{ __typename }' '{}' "$request_timeout" 2>/dev/null) &&
			[ "$(printf '%s' "$response" | jq -r '.data.__typename // empty')" != "" ]; then
			echo "${name} is ready"
			return 0
		fi
		sleep "$POLL_INTERVAL"
	done
	echo "Error: ${name} did not become ready at ${url}. Last response: ${response:-none}" >&2
	return 1
}

mutate() {
	local description=$1
	local query=$2
	local variables=$3
	local response
	response=$(graphql "$WRITE_API_SERVER_BASE_URL" "$query" "$variables")
	if [ "$(printf '%s' "$response" | jq -r 'has("errors")')" = "true" ]; then
		echo "Error: ${description}: ${response}" >&2
		exit 1
	fi
	printf '%s\n' "$response"
}

# The read model is updated from the head Streams asynchronously, so wait for the
# expected value instead of reading once after a fixed sleep.
poll_read() {
	local description=$1
	local filter=$2
	local query=$3
	local variables=$4
	local response=""
	local deadline=$((SECONDS + POLL_TIMEOUT))
	while [ "$SECONDS" -lt "$deadline" ]; do
		local request_timeout
		request_timeout=$(request_timeout_for_deadline "$deadline")
		if response=$(graphql "$READ_API_SERVER_BASE_URL" "$query" "$variables" "$request_timeout"); then
			if [ "$(printf '%s' "$response" | jq -r "$filter" 2>/dev/null || printf 'false')" = "true" ]; then
				echo "Read model reflected: ${description}"
				printf '%s\n' "$response"
				return 0
			fi
		else
			response="request failed or timed out"
		fi
		sleep "$POLL_INTERVAL"
	done
	echo "Error: ${description} was not reflected within ${POLL_TIMEOUT}s. Last response: ${response}" >&2
	return 1
}

variables_for_group_chat() {
	local group_chat_id=$1
	local user_account_id=$2
	jq -cn --arg groupChatId "$group_chat_id" --arg userAccountId "$user_account_id" \
		'{groupChatId: $groupChatId, userAccountId: $userAccountId}'
}

variables_for_message() {
	local message_id=$1
	local user_account_id=$2
	jq -cn --arg messageId "$message_id" --arg userAccountId "$user_account_id" \
		'{messageId: $messageId, userAccountId: $userAccountId}'
}

echo "Write API: ${WRITE_API_SERVER_BASE_URL}"
echo "Read API: ${READ_API_SERVER_BASE_URL}"

wait_for_graphql "Write API" "${WRITE_API_SERVER_BASE_URL}"
wait_for_graphql "Read API" "${READ_API_SERVER_BASE_URL}"

echo -e "\nCreate GroupChat(${ADMIN_ID}):"
CREATE_RESULT=$(mutate "createGroupChat" "${CREATE_GROUP_CHAT}" \
	"$(jq -cn --arg name "group-chat-example" --arg executorId "${ADMIN_ID}" \
		'{input: {name: $name, executorId: $executorId}}')")
GROUP_CHAT_ID=$(printf '%s' "${CREATE_RESULT}" | jq -r '.data.createGroupChat.groupChatId')
if [ -z "${GROUP_CHAT_ID}" ] || [ "${GROUP_CHAT_ID}" = "null" ]; then
	echo "Error: createGroupChat returned no groupChatId: ${CREATE_RESULT}" >&2
	exit 1
fi
echo "GROUP_CHAT_ID=${GROUP_CHAT_ID}"
poll_read "GroupChat creation" '.data.getGroupChat.name == "group-chat-example"' \
	"${GET_GROUP_CHAT}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${ADMIN_ID}")"

echo -e "\nAdd Member(${GROUP_CHAT_ID}, ${USER_ACCOUNT_ID}, ${ADMIN_ID}):"
mutate "addMember" "${ADD_MEMBER}" \
	"$(jq -cn --arg groupChatId "${GROUP_CHAT_ID}" --arg userAccountId "${USER_ACCOUNT_ID}" \
		--arg executorId "${ADMIN_ID}" \
		'{input: {groupChatId: $groupChatId, userAccountId: $userAccountId, role: "MEMBER", executorId: $executorId}}')"
poll_read "member addition" '.data.getMembers | length == 2' \
	"${GET_MEMBERS}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${USER_ACCOUNT_ID}")"
poll_read "member lookup" '.data.getMember != null' \
	"${GET_MEMBER}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${USER_ACCOUNT_ID}")"

echo -e "\nPost Message(${GROUP_CHAT_ID}, ${USER_ACCOUNT_ID}):"
POST_RESULT=$(mutate "postMessage" "${POST_MESSAGE}" \
	"$(jq -cn --arg groupChatId "${GROUP_CHAT_ID}" --arg content "Text1" \
		--arg executorId "${USER_ACCOUNT_ID}" \
		'{input: {groupChatId: $groupChatId, content: $content, executorId: $executorId}}')")
MESSAGE_ID=$(printf '%s' "${POST_RESULT}" | jq -r '.data.postMessage.messageId')
if [ -z "${MESSAGE_ID}" ] || [ "${MESSAGE_ID}" = "null" ]; then
	echo "Error: postMessage returned no messageId: ${POST_RESULT}" >&2
	exit 1
fi
echo "MESSAGE_ID=${MESSAGE_ID}"
poll_read "message posting" '.data.getMessage.text == "Text1"' \
	"${GET_MESSAGE}" "$(variables_for_message "${MESSAGE_ID}" "${USER_ACCOUNT_ID}")"
poll_read "message list" '.data.getMessages | length == 1' \
	"${GET_MESSAGES}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${USER_ACCOUNT_ID}")"
poll_read "group chat list" '.data.getGroupChats | length == 1' \
	"${GET_GROUP_CHATS}" "$(jq -cn --arg userAccountId "${ADMIN_ID}" '{userAccountId: $userAccountId}')"

echo -e "\nRename GroupChat(${GROUP_CHAT_ID}, ${ADMIN_ID}):"
mutate "renameGroupChat" "${RENAME_GROUP_CHAT}" \
	"$(jq -cn --arg groupChatId "${GROUP_CHAT_ID}" --arg name "group-chat-example-2" \
		--arg executorId "${ADMIN_ID}" \
		'{input: {groupChatId: $groupChatId, name: $name, executorId: $executorId}}')"
poll_read "GroupChat rename" '.data.getGroupChat.name == "group-chat-example-2"' \
	"${GET_GROUP_CHAT}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${ADMIN_ID}")"

echo -e "\nEdit Message(${GROUP_CHAT_ID}, ${MESSAGE_ID}, ${USER_ACCOUNT_ID}):"
mutate "editMessage" "${EDIT_MESSAGE}" \
	"$(jq -cn --arg groupChatId "${GROUP_CHAT_ID}" --arg messageId "${MESSAGE_ID}" \
		--arg content "Text2" --arg executorId "${USER_ACCOUNT_ID}" \
		'{input: {groupChatId: $groupChatId, messageId: $messageId, content: $content, executorId: $executorId}}')"
poll_read "message edit" '.data.getMessage.text == "Text2"' \
	"${GET_MESSAGE}" "$(variables_for_message "${MESSAGE_ID}" "${USER_ACCOUNT_ID}")"

echo -e "\nDelete Message(${GROUP_CHAT_ID}, ${MESSAGE_ID}, ${USER_ACCOUNT_ID}):"
mutate "deleteMessage" "${DELETE_MESSAGE}" \
	"$(jq -cn --arg groupChatId "${GROUP_CHAT_ID}" --arg messageId "${MESSAGE_ID}" \
		--arg executorId "${USER_ACCOUNT_ID}" \
		'{input: {groupChatId: $groupChatId, messageId: $messageId, executorId: $executorId}}')"
poll_read "message deletion" '(has("errors") | not) and (.data.getMessages == [])' \
	"${GET_MESSAGES}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${USER_ACCOUNT_ID}")"

echo -e "\nRemove Member(${GROUP_CHAT_ID}, ${USER_ACCOUNT_ID}, ${ADMIN_ID}):"
mutate "removeMember" "${REMOVE_MEMBER}" \
	"$(jq -cn --arg groupChatId "${GROUP_CHAT_ID}" --arg userAccountId "${USER_ACCOUNT_ID}" \
		--arg executorId "${ADMIN_ID}" \
		'{input: {groupChatId: $groupChatId, userAccountId: $userAccountId, executorId: $executorId}}')"
poll_read "member removal" '.data.getMembers | length == 1' \
	"${GET_MEMBERS}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${ADMIN_ID}")"

echo -e "\nDelete GroupChat(${GROUP_CHAT_ID}, ${ADMIN_ID}):"
mutate "deleteGroupChat" "${DELETE_GROUP_CHAT}" \
	"$(jq -cn --arg groupChatId "${GROUP_CHAT_ID}" --arg executorId "${ADMIN_ID}" \
		'{input: {groupChatId: $groupChatId, executorId: $executorId}}')"
poll_read "GroupChat deletion" '.errors[0].extensions.code == "404"' \
	"${GET_GROUP_CHAT}" "$(variables_for_group_chat "${GROUP_CHAT_ID}" "${ADMIN_ID}")"
poll_read "GroupChat list after deletion" '(has("errors") | not) and (.data.getGroupChats == [])' \
	"${GET_GROUP_CHATS}" "$(jq -cn --arg userAccountId "${ADMIN_ID}" '{userAccountId: $userAccountId}')"

echo -e "\nEnd-to-end verification succeeded"
