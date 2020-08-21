drop index if exists pt.publish_token_origin;
create index publish_token_origin on pt.publish_token(origin_service, origin_user);
