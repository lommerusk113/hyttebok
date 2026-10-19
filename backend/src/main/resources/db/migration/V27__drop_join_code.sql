-- Invitation links are the only way into a cabin: the join code and the access requests it made are gone.
drop table access_request;
alter table cabin_info drop column join_code;
update push_subscription set topics = array_remove(topics, 'ACCESS');
