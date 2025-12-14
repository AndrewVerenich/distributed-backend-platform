create table task_execution
(
    id          bigserial primary key,
    uuid        varchar(36) not null,
    start_time  timestamp   not null,
    finish_time timestamp,
    name        varchar(100) not null,
    component   varchar(50)  not null,
    status      varchar(50)  not null
);

create index idx__task_execution__name
    on task_execution (name);

create index idx__task_execution__uuid
    on task_execution (uuid);
