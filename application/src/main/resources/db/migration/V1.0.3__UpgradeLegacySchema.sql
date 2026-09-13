-- Keep V1.0.0 and V1.0.1 immutable: dev already ran those historical versions.
-- Copy the legacy numeric relationships to the natural keys used by the current
-- entities. The original tables are retained with a LEGACY_V103 suffix so the
-- migration is auditable and the source data remains recoverable.

create table PROJECT_V103
(
    name                           varchar(255) not null,
    description                    varchar(255),
    sunat_username                 varchar(255) not null,
    sunat_password                 varchar(255) not null,
    sunat_url_factura              varchar(255) not null,
    sunat_url_guia_remision        varchar(255) not null,
    sunat_url_percepcion_retencion varchar(255) not null,
    sunat_client_id                varchar(255),
    sunat_client_secret            varchar(255),
    version                        int4         not null,
    primary key (name)
);

insert into PROJECT_V103
(
    name, description,
    sunat_username, sunat_password,
    sunat_url_factura, sunat_url_guia_remision, sunat_url_percepcion_retencion,
    sunat_client_id, sunat_client_secret,
    version
)
select
    name, description,
    sunat_username, sunat_password,
    sunat_url_factura, sunat_url_guia_remision, sunat_url_percepcion_retencion,
    sunat_client_id, sunat_client_secret,
    version
from PROJECT;

create table COMPANY_V103
(
    project                        varchar(255) not null,
    ruc                            varchar(11)  not null,
    name                           varchar(255) not null,
    description                    varchar(255),
    logo_file_id                   varchar(255),
    sunat_username                 varchar(255),
    sunat_password                 varchar(255),
    sunat_url_factura              varchar(255),
    sunat_url_guia_remision        varchar(255),
    sunat_url_percepcion_retencion varchar(255),
    sunat_client_id                varchar(255),
    sunat_client_secret            varchar(255),
    version                        int4         not null,
    primary key (project, ruc)
);

insert into COMPANY_V103
(
    project, ruc, name, description, logo_file_id,
    sunat_username, sunat_password,
    sunat_url_factura, sunat_url_guia_remision, sunat_url_percepcion_retencion,
    sunat_client_id, sunat_client_secret,
    version
)
select
    p.name, c.ruc, c.name, c.description, null,
    c.sunat_username, c.sunat_password,
    c.sunat_url_factura, c.sunat_url_guia_remision, c.sunat_url_percepcion_retencion,
    c.sunat_client_id, c.sunat_client_secret,
    c.version
from COMPANY c
join PROJECT p on p.id = c.project_id;

create table COMPONENT_V103
(
    id            varchar(255) not null,
    name          varchar(255) not null,
    parent_id     varchar(255),
    provider_id   varchar(255),
    provider_type varchar(255),
    sub_type      varchar(255),
    project       varchar(255) not null,
    ruc           varchar(11),
    primary key (id)
);

insert into COMPONENT_V103
(
    id, name, parent_id, provider_id, provider_type, sub_type, project, ruc
)
select
    cast(c.id as varchar(255)),
    c.name,
    cast(c.parent_id as varchar(255)),
    c.provider_id,
    c.provider_type,
    c.sub_type,
    coalesce(company_project.name, project_owner.name),
    company_owner.ruc
from COMPONENT c
left join COMPANY company_owner on company_owner.id = c.company_id
left join PROJECT company_project on company_project.id = company_owner.project_id
left join PROJECT project_owner on project_owner.id = c.project_id;

create table COMPONENT_CONFIG_V103
(
    id           int8         not null,
    name         varchar(255),
    val          varchar(4000),
    component_id varchar(255) not null,
    primary key (id)
);

insert into COMPONENT_CONFIG_V103 (id, name, val, component_id)
select id, name, value, cast(component_id as varchar(255))
from COMPONENT_CONFIG;

create table UBL_DOCUMENT_V103
(
    id                             int8         not null,
    project                        varchar(255) not null,
    job_in_progress                char(1)      not null,
    xml_file_id                    varchar(255) not null,
    cdr_file_id                    varchar(255),
    created                        timestamp    not null,
    updated                        timestamp,
    version                        int4         not null,
    xml_ruc                        varchar(11),
    xml_serie_numero               varchar(50),
    xml_tipo_documento             varchar(50),
    xml_baja_codigo_tipo_documento varchar(50),
    sunat_code                     int4,
    sunat_description              varchar(255),
    sunat_status                   varchar(50),
    sunat_ticket                   varchar(50),
    error_description              varchar(255),
    error_phase                    varchar(255),
    error_recovery_action          varchar(255),
    error_count                    int4,
    primary key (id)
);

insert into UBL_DOCUMENT_V103
(
    id, project, job_in_progress, xml_file_id, cdr_file_id,
    created, updated, version,
    xml_ruc, xml_serie_numero, xml_tipo_documento, xml_baja_codigo_tipo_documento,
    sunat_code, sunat_description, sunat_status, sunat_ticket,
    error_description, error_phase, error_recovery_action, error_count
)
select
    d.id, p.name, d.job_in_progress, d.xml_file_id, d.cdr_file_id,
    d.created, d.updated, d.version,
    d.xml_ruc, d.xml_serie_numero, d.xml_tipo_documento, d.xml_baja_codigo_tipo_documento,
    d.sunat_code, d.sunat_description, d.sunat_status, d.sunat_ticket,
    d.error_description, d.error_phase, d.error_recovery_action, d.error_count
from UBL_DOCUMENT d
join PROJECT p on p.id = d.project_id;

create table SUNAT_NOTE_V103
(
    sunat_note_id int8 not null,
    val           varchar(255)
);

insert into SUNAT_NOTE_V103 (sunat_note_id, val)
select sunat_note_id, value
from SUNAT_NOTE;

create table GENERATED_ID_V103
(
    id            int8         not null,
    project       varchar(255) not null,
    ruc           varchar(11)  not null,
    document_type varchar(50)  not null,
    serie         int4         not null,
    numero        int4         not null,
    created       timestamp    not null,
    updated       timestamp,
    version       int4         not null,
    primary key (id)
);

insert into GENERATED_ID_V103
(
    id, project, ruc, document_type, serie, numero, created, updated, version
)
select
    g.id, p.name, g.ruc, g.document_type, g.serie, g.numero,
    g.created, g.updated, g.version
from GENERATED_ID g
join PROJECT p on p.id = g.project_id;

create table QUTE_TEMPLATE
(
    id            int8          not null,
    content       varchar(4000) not null,
    template_type varchar(50)   not null,
    document_type varchar(50)   not null,
    project       varchar(255)  not null,
    ruc           varchar(11),
    primary key (id)
);

create table PROJECT_USER
(
    project  varchar(255) not null,
    username varchar(250) not null,
    roles    varchar(250) not null,
    version  int4         not null,
    primary key (project, username)
);

-- Legacy authorization was global. Preserve equivalent access by assigning
-- every legacy user to every existing project. Write/admin users become owners.
insert into PROJECT_USER (project, username, roles, version)
select
    p.name,
    u.username,
    case
        when u.permissions like '%admin:app%'
          or u.permissions like '%project:write%'
            then 'owner'
        else 'member'
    end,
    u.version
from PROJECT_V103 p
cross join APP_USER u;

alter table SUNAT_NOTE rename to SUNAT_NOTE_LEGACY_V103;
alter table COMPONENT_CONFIG rename to COMPONENT_CONFIG_LEGACY_V103;
alter table COMPONENT rename to COMPONENT_LEGACY_V103;
alter table UBL_DOCUMENT rename to UBL_DOCUMENT_LEGACY_V103;
alter table GENERATED_ID rename to GENERATED_ID_LEGACY_V103;
alter table COMPANY rename to COMPANY_LEGACY_V103;
alter table PROJECT rename to PROJECT_LEGACY_V103;

alter table PROJECT_V103 rename to PROJECT;
alter table COMPANY_V103 rename to COMPANY;
alter table COMPONENT_V103 rename to COMPONENT;
alter table COMPONENT_CONFIG_V103 rename to COMPONENT_CONFIG;
alter table UBL_DOCUMENT_V103 rename to UBL_DOCUMENT;
alter table SUNAT_NOTE_V103 rename to SUNAT_NOTE;
alter table GENERATED_ID_V103 rename to GENERATED_ID;

alter table COMPONENT
    add constraint fk_component_project_v103
        foreign key (project) references PROJECT on delete cascade;

alter table COMPONENT_CONFIG
    add constraint fk_componentconfig_component_v103
        foreign key (component_id) references COMPONENT on delete cascade;

alter table COMPANY
    add constraint fk_company_project_v103
        foreign key (project) references PROJECT on delete cascade;

alter table UBL_DOCUMENT
    add constraint fk_ubldocument_project_v103
        foreign key (project) references PROJECT on delete cascade;

alter table SUNAT_NOTE
    add constraint fk_sunatnote_ubldocument_v103
        foreign key (sunat_note_id) references UBL_DOCUMENT on delete cascade;

alter table GENERATED_ID
    add constraint fk_generatedid_project_v103
        foreign key (project) references PROJECT on delete cascade;

alter table GENERATED_ID
    add constraint uq_generatedid_project_ruc_documenttype_v103
        unique (project, ruc, document_type);

alter table PROJECT_USER
    add constraint fk_projectuser_project_v103
        foreign key (project) references PROJECT on delete cascade;
