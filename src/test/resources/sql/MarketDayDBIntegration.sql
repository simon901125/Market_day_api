USE master;
GO

/* =========================================================
   Rebuild SQL Server integration-test database
   WARNING: Running this script deletes MarketDayDB_Test only.
   Keep this test schema synchronized with sql/MarketDayDB.sql.
   ========================================================= */

IF DB_ID(N'MarketDayDB_Test') IS NOT NULL
BEGIN
    ALTER DATABASE MarketDayDB_Test SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE MarketDayDB_Test;
END
GO

CREATE DATABASE MarketDayDB_Test;
GO

USE MarketDayDB_Test;
GO

/* =========================================================
   小集日 Market Day - MVP SQL Server Database
   Source: 資料庫規劃.md
   ========================================================= */

CREATE TABLE dbo.users
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    role VARCHAR(30) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NULL,
    provider VARCHAR(30) NOT NULL,
    google_sub VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL CONSTRAINT DF_users_status DEFAULT 'UNACTIVE',
    isLogin BIT NOT NULL CONSTRAINT DF_users_isLogin DEFAULT 0,
    email_verified_at DATETIME2(0) NULL,
    expired_time DATETIME2(0) NOT NULL CONSTRAINT DF_users_expired_time DEFAULT SYSDATETIME(),
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_users_created_at DEFAULT SYSDATETIME(),
    updated_at DATETIME2(0) NOT NULL CONSTRAINT DF_users_updated_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_users PRIMARY KEY (id),
    CONSTRAINT UQ_users_email UNIQUE (email),
    CONSTRAINT CK_users_role CHECK (role IN ('VENDOR', 'ORGANIZER', 'ADMIN')),
    CONSTRAINT CK_users_provider CHECK (provider IN ('LOCAL', 'GOOGLE', 'BOTH')),
    CONSTRAINT CK_users_provider_google_sub CHECK (
        (provider = 'LOCAL' AND google_sub IS NULL)
        OR (provider IN ('GOOGLE', 'BOTH') AND google_sub IS NOT NULL)
    ),
    CONSTRAINT CK_users_status CHECK (status IN ('UNACTIVE', 'ACTIVE', 'DISABLED', 'IS_DELETED'))
);
GO

CREATE INDEX IX_users_role_status ON dbo.users(role, status);
CREATE INDEX IX_users_provider ON dbo.users(provider);
CREATE UNIQUE INDEX UX_users_google_sub ON dbo.users(google_sub) WHERE google_sub IS NOT NULL;
CREATE INDEX IX_users_isLogin_expired_time ON dbo.users(isLogin, expired_time);
GO

CREATE TRIGGER dbo.trg_users_activate_after_email_verified
ON dbo.users
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE users
    SET status = 'ACTIVE'
    FROM dbo.users users
        INNER JOIN inserted inserted_users ON users.id = inserted_users.id
    WHERE inserted_users.email_verified_at IS NOT NULL
        AND users.status = 'UNACTIVE';
END;
GO

CREATE TABLE dbo.user_tokens
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    token VARCHAR(100) NOT NULL,
    token_type VARCHAR(30) NOT NULL,
    expires_at DATETIME2(0) NOT NULL,
    CONSTRAINT PK_user_tokens PRIMARY KEY (id),
    CONSTRAINT UQ_user_tokens_token UNIQUE (token),
    CONSTRAINT FK_user_tokens_users FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT CK_user_tokens_type CHECK (token_type IN ('EMAIL_VERIFY', 'PASSWORD_RESET'))
);
GO

CREATE INDEX IX_user_tokens_user_type ON dbo.user_tokens(user_id, token_type);
CREATE INDEX IX_user_tokens_type_expires ON dbo.user_tokens(token_type, expires_at);
GO

CREATE TABLE dbo.categories
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    name NVARCHAR(100) NOT NULL,
    slug NVARCHAR(100) NOT NULL,
    is_active BIT NOT NULL CONSTRAINT DF_categories_is_active DEFAULT 1,
    CONSTRAINT PK_categories PRIMARY KEY (id),
    CONSTRAINT UQ_categories_slug UNIQUE (slug)
);
GO

CREATE TABLE dbo.user_profiles
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    profile_type NVARCHAR(30) NOT NULL,
    contact_name NVARCHAR(100) NULL,
    contact_phone NVARCHAR(30) NULL,
    contact_email NVARCHAR(255) NULL,
    city NVARCHAR(50) NULL,
    district NVARCHAR(50) NULL,
    address NVARCHAR(255) NULL,
    CONSTRAINT PK_user_profiles PRIMARY KEY (id),
    CONSTRAINT FK_user_profiles_users FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT CK_user_profiles_profile_type CHECK (profile_type IN (N'VENDOR', N'ORGANIZER'))
);
GO

CREATE INDEX IX_user_profiles_user_profile_type ON dbo.user_profiles(user_id, profile_type);
GO

CREATE TABLE dbo.vendor_profiles
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_profile_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    brand_name NVARCHAR(150) NULL,
    avatar_image_url NVARCHAR(500) NULL,
    cover_image_url NVARCHAR(500) NULL,
    instagram_url NVARCHAR(500) NULL,
    facebook_url NVARCHAR(500) NULL,
    website_url NVARCHAR(500) NULL,
    brand_summary NVARCHAR(300) NULL,
    brand_description NVARCHAR(MAX) NULL,
    CONSTRAINT PK_vendor_profiles PRIMARY KEY (id),
    CONSTRAINT UQ_vendor_profiles_user_profile UNIQUE (user_profile_id),
    CONSTRAINT FK_vendor_profiles_user_profiles FOREIGN KEY (user_profile_id) REFERENCES dbo.user_profiles(id),
    CONSTRAINT FK_vendor_profiles_categories FOREIGN KEY (category_id) REFERENCES dbo.categories(id)
);
GO

CREATE INDEX IX_vendor_profiles_category ON dbo.vendor_profiles(category_id, id);
GO

CREATE TABLE dbo.organizer_profiles
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_profile_id BIGINT NOT NULL,
    organizer_name NVARCHAR(150) NULL,
    company_name NVARCHAR(150) NULL,
    tax_id NVARCHAR(20) NULL,
    service_days NVARCHAR(100) NULL,
    service_start_time TIME(0) NULL,
    service_end_time TIME(0) NULL,
    CONSTRAINT PK_organizer_profiles PRIMARY KEY (id),
    CONSTRAINT UQ_organizer_profiles_user_profile UNIQUE (user_profile_id),
    CONSTRAINT FK_organizer_profiles_user_profiles FOREIGN KEY (user_profile_id) REFERENCES dbo.user_profiles(id)
);
GO

CREATE TRIGGER dbo.trg_vendor_profiles_validate_profile_type
ON dbo.vendor_profiles
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        INNER JOIN dbo.user_profiles up ON up.id = i.user_profile_id
        WHERE up.profile_type <> N'VENDOR'
    )
    BEGIN
        THROW 50001, N'vendor_profiles.user_profile_id must reference a VENDOR profile.', 1;
    END
END
GO

CREATE TRIGGER dbo.trg_organizer_profiles_validate_profile_type
ON dbo.organizer_profiles
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        INNER JOIN dbo.user_profiles up ON up.id = i.user_profile_id
        WHERE up.profile_type <> N'ORGANIZER'
    )
    BEGIN
        THROW 50002, N'organizer_profiles.user_profile_id must reference an ORGANIZER profile.', 1;
    END
END
GO

CREATE TABLE dbo.vendor_products
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    vendor_profile_id BIGINT NOT NULL,
    name NVARCHAR(150) NOT NULL,
    short_description NVARCHAR(255) NOT NULL,
    description NVARCHAR(MAX) NULL,
    price DECIMAL(10,2) NULL,
    image_url NVARCHAR(500) NULL,
    CONSTRAINT PK_vendor_products PRIMARY KEY (id),
    CONSTRAINT FK_vendor_products_vendor_profiles FOREIGN KEY (vendor_profile_id) REFERENCES dbo.vendor_profiles(id)
);
GO

CREATE TABLE dbo.admin_profiles
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    admin_name NVARCHAR(100) NOT NULL,
    CONSTRAINT PK_admin_profiles PRIMARY KEY (id),
    CONSTRAINT UQ_admin_profiles_user UNIQUE (user_id),
    CONSTRAINT FK_admin_profiles_users FOREIGN KEY (user_id) REFERENCES dbo.users(id)
);
GO

CREATE TRIGGER dbo.trg_admin_profiles_validate_role
ON dbo.admin_profiles
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (
        SELECT 1
        FROM inserted i
        INNER JOIN dbo.users u ON u.id = i.user_id
        WHERE u.role <> 'ADMIN'
    )
    BEGIN
        THROW 50003, N'admin_profiles.user_id must reference an ADMIN user.', 1;
    END
END
GO

CREATE INDEX IX_vendor_products_vendor
ON dbo.vendor_products(vendor_profile_id);
GO

CREATE TABLE dbo.market_events
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    title NVARCHAR(200) NULL,
    summary NVARCHAR(300) NULL,
    description NVARCHAR(MAX) NULL,
    location_name NVARCHAR(200) NULL,
    city NVARCHAR(50) NULL,
    district NVARCHAR(50) NULL,
    address NVARCHAR(255) NULL,
    notice NVARCHAR(MAX) NULL,
    start_at DATETIME2(0) NULL,
    end_at DATETIME2(0) NULL,
    registration_start_at DATETIME2(0) NULL,
    registration_end_at DATETIME2(0) NULL,
    max_booths INT NULL,
    stall_width DECIMAL(6,2) NULL,
    stall_length DECIMAL(6,2) NULL,
    base_fee DECIMAL(10,2) NULL,
    deposit_amount DECIMAL(10,2) NULL CONSTRAINT DF_market_events_deposit_amount DEFAULT 0,
    traffic_info_driving NVARCHAR(MAX) NULL,
    traffic_info_bus NVARCHAR(MAX) NULL,
    traffic_info_metro NVARCHAR(MAX) NULL,
    cover_image_url NVARCHAR(500) NULL,
    map_image_url NVARCHAR(500) NULL,
    public_info_at DATETIME2(0) NULL,
    brands_public_at DATETIME2(0) NULL,
    workflow_status NVARCHAR(30) NOT NULL CONSTRAINT DF_market_events_workflow_status DEFAULT N'DRAFT',
    review_note NVARCHAR(MAX) NULL,
    create_at DATETIME2(0) NOT NULL CONSTRAINT DF_market_events_create_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_market_events PRIMARY KEY (id),
    CONSTRAINT FK_market_events_users FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT CK_market_events_date_range CHECK (end_at >= start_at),
    CONSTRAINT CK_market_events_registration_range CHECK (registration_end_at >= registration_start_at),
    CONSTRAINT CK_market_events_workflow_status CHECK (workflow_status IN (N'DRAFT', N'PENDING_REVIEW', N'REVISION_REQUIRED', N'MAP_BUILDING', N'READY_TO_PUBLISH', N'PUBLISHED', N'FINAL_REVIEW', N'UNPUBLISH_REQUESTED', N'UNPUBLISHED', N'CANCELLED'))
);
GO

CREATE INDEX IX_market_events_user ON dbo.market_events(user_id);
CREATE INDEX IX_market_events_dates ON dbo.market_events(start_at, end_at);
CREATE INDEX IX_market_events_city ON dbo.market_events(city);
CREATE INDEX IX_market_events_workflow_status ON dbo.market_events(workflow_status);
GO

CREATE TABLE dbo.market_event_categories
(
    event_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    CONSTRAINT PK_market_event_categories PRIMARY KEY (event_id, category_id),
    CONSTRAINT FK_market_event_categories_events FOREIGN KEY (event_id) REFERENCES dbo.market_events(id) ON DELETE CASCADE,
    CONSTRAINT FK_market_event_categories_categories FOREIGN KEY (category_id) REFERENCES dbo.categories(id)
);
GO

CREATE INDEX IX_market_event_categories_category
ON dbo.market_event_categories(category_id, event_id);
GO

CREATE TABLE dbo.event_unpublish_requests
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    reviewed_by BIGINT NULL,
    reason NVARCHAR(MAX) NOT NULL,
    status NVARCHAR(30) NOT NULL CONSTRAINT DF_event_unpublish_requests_status DEFAULT N'PENDING',
    review_note NVARCHAR(MAX) NULL,
    requested_at DATETIME2(0) NOT NULL CONSTRAINT DF_event_unpublish_requests_requested_at DEFAULT SYSDATETIME(),
    reviewed_at DATETIME2(0) NULL,
    CONSTRAINT PK_event_unpublish_requests PRIMARY KEY (id),
    CONSTRAINT FK_event_unpublish_requests_market_events FOREIGN KEY (event_id) REFERENCES dbo.market_events(id),
    CONSTRAINT FK_event_unpublish_requests_requested_by FOREIGN KEY (requested_by) REFERENCES dbo.users(id),
    CONSTRAINT FK_event_unpublish_requests_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES dbo.users(id),
    CONSTRAINT CK_event_unpublish_requests_status CHECK (status IN (N'PENDING', N'APPROVED', N'REJECTED', N'CANCELLED'))
);
GO

CREATE INDEX IX_event_unpublish_requests_event_status ON dbo.event_unpublish_requests(event_id, status);
CREATE INDEX IX_event_unpublish_requests_requested_by ON dbo.event_unpublish_requests(requested_by);
CREATE INDEX IX_event_unpublish_requests_reviewed_by ON dbo.event_unpublish_requests(reviewed_by);
CREATE INDEX IX_event_unpublish_requests_requested_at ON dbo.event_unpublish_requests(requested_at);
GO

CREATE TABLE dbo.event_stall_zones
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id BIGINT NOT NULL,
    zone_name NVARCHAR(50) NOT NULL,
    zone_color NVARCHAR(20) NULL,
    stall_count INT NOT NULL,
    CONSTRAINT PK_event_stall_zones PRIMARY KEY (id),
    CONSTRAINT FK_event_stall_zones_market_events FOREIGN KEY (event_id) REFERENCES dbo.market_events(id),
    CONSTRAINT CK_event_stall_zones_stall_count CHECK (stall_count >= 0)
);
GO

CREATE INDEX IX_event_stall_zones_event ON dbo.event_stall_zones(event_id);
GO

CREATE TABLE dbo.event_stalls
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id BIGINT NOT NULL,
    zone_id BIGINT NOT NULL,
    stall_no NVARCHAR(30) NOT NULL,
    status NVARCHAR(30) NOT NULL CONSTRAINT DF_event_stalls_status DEFAULT N'AVAILABLE',
    CONSTRAINT PK_event_stalls PRIMARY KEY (id),
    CONSTRAINT UQ_event_stalls_event_stall_no UNIQUE (event_id, stall_no),
    CONSTRAINT FK_event_stalls_market_events FOREIGN KEY (event_id) REFERENCES dbo.market_events(id),
    CONSTRAINT FK_event_stalls_event_stall_zones FOREIGN KEY (zone_id) REFERENCES dbo.event_stall_zones(id),
    CONSTRAINT CK_event_stalls_status CHECK (status IN (N'AVAILABLE', N'ASSIGNED', N'DISABLED'))
);
GO

CREATE INDEX IX_event_stalls_event_status ON dbo.event_stalls(event_id, status);
CREATE INDEX IX_event_stalls_zone ON dbo.event_stalls(zone_id);
GO

CREATE TABLE dbo.event_equipments
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    event_id BIGINT NOT NULL,
    equipment_group_key NVARCHAR(100) NULL,
    name NVARCHAR(100) NOT NULL,
    rental_fee DECIMAL(10,2) NOT NULL,
    pricing_unit NVARCHAR(20) NOT NULL,
    unit NVARCHAR(20) NULL,
    charge_type NVARCHAR(20) NOT NULL CONSTRAINT DF_event_equipments_charge_type DEFAULT N'PAID',
    item_type NVARCHAR(20) NOT NULL CONSTRAINT DF_event_equipments_item_type DEFAULT N'EQUIPMENT',
    description NVARCHAR(1000) NULL,
    stock_quantity INT NULL,
    per_stall_rental_limit INT NULL,
    rental_status NVARCHAR(20) NOT NULL CONSTRAINT DF_event_equipments_rental_status DEFAULT N'ACTIVE',
    wattage_limit INT NULL,
    CONSTRAINT PK_event_equipments PRIMARY KEY (id),
    CONSTRAINT FK_event_equipments_market_events FOREIGN KEY (event_id) REFERENCES dbo.market_events(id),
    CONSTRAINT CK_event_equipments_pricing_unit CHECK (pricing_unit IN (N'HOUR', N'DAY')),
    CONSTRAINT CK_event_equipments_charge_type CHECK (charge_type IN (N'FREE', N'PAID')),
    CONSTRAINT CK_event_equipments_item_type CHECK (item_type IN (N'EQUIPMENT', N'POWER')),
    CONSTRAINT CK_event_equipments_unit_item_type CHECK (
        (item_type = N'EQUIPMENT' AND unit IS NOT NULL)
        OR (item_type <> N'EQUIPMENT' AND unit IS NULL)
    ),
    CONSTRAINT CK_event_equipments_rental_fee CHECK (rental_fee >= 0),
    CONSTRAINT CK_event_equipments_stock_quantity CHECK (stock_quantity IS NULL OR stock_quantity >= 0),
    CONSTRAINT CK_event_equipments_per_stall_rental_limit CHECK (per_stall_rental_limit IS NULL OR per_stall_rental_limit > 0),
    CONSTRAINT CK_event_equipments_rental_status CHECK (rental_status IN (N'ACTIVE', N'UNACTIVE')),
    CONSTRAINT CK_event_equipments_group_key CHECK (equipment_group_key IS NULL OR LTRIM(RTRIM(equipment_group_key)) <> N''),
    CONSTRAINT CK_event_equipments_wattage_limit CHECK (wattage_limit IS NULL OR wattage_limit > 0)
);
GO

CREATE INDEX IX_event_equipments_event ON dbo.event_equipments(event_id);
CREATE INDEX IX_event_equipments_event_group_key ON dbo.event_equipments(event_id, equipment_group_key);
CREATE INDEX IX_event_equipments_event_charge_item ON dbo.event_equipments(event_id, charge_type, item_type);
CREATE INDEX IX_event_equipments_event_rental_status ON dbo.event_equipments(event_id, rental_status);
GO

CREATE TABLE dbo.event_applications
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    application_no NVARCHAR(30) NOT NULL,
    event_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vendor_profile_id BIGINT NOT NULL,
    vehicle_no NVARCHAR(30) NULL,
    applicant_note NVARCHAR(MAX) NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    deposit_amount DECIMAL(10,2) NOT NULL CONSTRAINT DF_event_applications_deposit_amount DEFAULT 0,
    deposit_status NVARCHAR(30) NOT NULL CONSTRAINT DF_event_applications_deposit_status DEFAULT N'NOT_RETURNED',
    payment_due_at DATETIME2(0) NULL,
    review_status NVARCHAR(30) NOT NULL CONSTRAINT DF_event_applications_review_status DEFAULT N'PENDING',
    payment_status NVARCHAR(30) NOT NULL CONSTRAINT DF_event_applications_payment_status DEFAULT N'PENDING',
    is_cancelled BIT NOT NULL CONSTRAINT DF_event_applications_is_cancelled DEFAULT 0,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_event_applications_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_event_applications PRIMARY KEY (id),
    CONSTRAINT UQ_event_applications_application_no UNIQUE (application_no),
    CONSTRAINT UQ_event_applications_event_vendor_profile UNIQUE (event_id, vendor_profile_id),
    CONSTRAINT FK_event_applications_market_events FOREIGN KEY (event_id) REFERENCES dbo.market_events(id),
    CONSTRAINT FK_event_applications_users FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT FK_event_applications_vendor_profiles FOREIGN KEY (vendor_profile_id) REFERENCES dbo.vendor_profiles(id),
    CONSTRAINT CK_event_applications_review_status CHECK (review_status IN (N'PENDING', N'APPROVED', N'REJECTED')),
    CONSTRAINT CK_event_applications_deposit_status CHECK (deposit_status IN (N'NOT_RETURNED', N'RETURNED')),
    CONSTRAINT CK_event_applications_payment_status CHECK (payment_status IN (N'PENDING', N'PAID', N'FAILED', N'EXPIRED'))
);
GO

CREATE INDEX IX_event_applications_event_review ON dbo.event_applications(event_id, review_status);
CREATE INDEX IX_event_applications_user ON dbo.event_applications(user_id);
CREATE INDEX IX_event_applications_payment_due ON dbo.event_applications(payment_status, payment_due_at);
CREATE INDEX IX_event_applications_cancelled ON dbo.event_applications(is_cancelled);
CREATE INDEX IX_event_applications_created ON dbo.event_applications(created_at);
GO

CREATE TABLE dbo.application_review_notes
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    application_id BIGINT NOT NULL,
    review_note NVARCHAR(100) NOT NULL,
    review_note_detail NVARCHAR(MAX) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_application_review_notes_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_application_review_notes PRIMARY KEY (id),
    CONSTRAINT FK_application_review_notes_event_applications FOREIGN KEY (application_id) REFERENCES dbo.event_applications(id)
);
GO

CREATE INDEX IX_application_review_notes_application ON dbo.application_review_notes(application_id, created_at);
GO

CREATE TABLE dbo.equipment_rentals
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    application_id BIGINT NOT NULL,
    event_equipment_id BIGINT NOT NULL,
    equipment_name NVARCHAR(100) NOT NULL,
    rental_fee DECIMAL(10,2) NOT NULL,
    pricing_unit NVARCHAR(20) NOT NULL,
    unit NVARCHAR(20) NULL,
    quantity INT NOT NULL,
    rental_units INT NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    CONSTRAINT PK_equipment_rentals PRIMARY KEY (id),
    CONSTRAINT FK_equipment_rentals_event_applications FOREIGN KEY (application_id) REFERENCES dbo.event_applications(id),
    CONSTRAINT FK_equipment_rentals_event_equipments FOREIGN KEY (event_equipment_id) REFERENCES dbo.event_equipments(id),
    CONSTRAINT CK_equipment_rentals_pricing_unit CHECK (pricing_unit IN (N'HOUR', N'DAY')),
    CONSTRAINT CK_equipment_rentals_rental_fee CHECK (rental_fee >= 0),
    CONSTRAINT CK_equipment_rentals_quantity CHECK (quantity > 0),
    CONSTRAINT CK_equipment_rentals_rental_units CHECK (rental_units > 0),
    CONSTRAINT CK_equipment_rentals_subtotal CHECK (subtotal >= 0)
);
GO

CREATE INDEX IX_equipment_rentals_application ON dbo.equipment_rentals(application_id);
CREATE INDEX IX_equipment_rentals_event_equipment ON dbo.equipment_rentals(event_equipment_id);
GO

CREATE TABLE dbo.rental_appliances
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    equipment_rental_id BIGINT NOT NULL,
    appliance_name NVARCHAR(100) NOT NULL,
    wattage INT NOT NULL,
    CONSTRAINT PK_rental_appliances PRIMARY KEY (id),
    CONSTRAINT FK_rental_appliances_equipment_rentals FOREIGN KEY (equipment_rental_id) REFERENCES dbo.equipment_rentals(id),
    CONSTRAINT CK_rental_appliances_wattage CHECK (wattage > 0)
);
GO

CREATE INDEX IX_rental_appliances_equipment_rental ON dbo.rental_appliances(equipment_rental_id);
GO

CREATE TABLE dbo.application_dates
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    application_id BIGINT NOT NULL,
    apply_date DATE NOT NULL,
    selected_stall_id BIGINT NULL,
    CONSTRAINT PK_application_dates PRIMARY KEY (id),
    CONSTRAINT UQ_application_dates_application_date UNIQUE (application_id, apply_date),
    CONSTRAINT FK_application_dates_event_applications FOREIGN KEY (application_id) REFERENCES dbo.event_applications(id),
    CONSTRAINT FK_application_dates_event_stalls FOREIGN KEY (selected_stall_id) REFERENCES dbo.event_stalls(id)
);
GO

CREATE INDEX IX_application_dates_apply_date ON dbo.application_dates(apply_date);
CREATE INDEX IX_application_dates_selected_stall ON dbo.application_dates(selected_stall_id);
CREATE UNIQUE INDEX UX_application_dates_apply_date_selected_stall
ON dbo.application_dates(apply_date, selected_stall_id)
WHERE selected_stall_id IS NOT NULL;
GO

CREATE TABLE dbo.payments
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    payment_no NVARCHAR(40) NOT NULL,
    application_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    provider NVARCHAR(30) NULL,
    provider_trade_no NVARCHAR(100) NULL,
    provider_response_code NVARCHAR(20) NULL,
    provider_message NVARCHAR(255) NULL,
    status NVARCHAR(30) NOT NULL,
    paid_at DATETIME2(0) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_payments_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_payments PRIMARY KEY (id),
    CONSTRAINT UQ_payments_payment_no UNIQUE (payment_no),
    CONSTRAINT FK_payments_event_applications FOREIGN KEY (application_id) REFERENCES dbo.event_applications(id),
    CONSTRAINT CK_payments_status CHECK (status IN (N'PENDING', N'PAID', N'FAILED', N'EXPIRED'))
);
GO

CREATE INDEX IX_payments_application ON dbo.payments(application_id);
CREATE INDEX IX_payments_status ON dbo.payments(status);
GO

CREATE TABLE dbo.refunds
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    refund_no NVARCHAR(40) NOT NULL,
    application_id BIGINT NOT NULL,
    payment_id BIGINT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason NVARCHAR(255) NULL,
    failed_reason NVARCHAR(255) NULL,
    refund_status NVARCHAR(30) NOT NULL CONSTRAINT DF_refunds_refund_status DEFAULT N'REFUND_REQUESTED',
    refunded_at DATETIME2(0) NULL,
    CONSTRAINT PK_refunds PRIMARY KEY (id),
    CONSTRAINT UQ_refunds_refund_no UNIQUE (refund_no),
    CONSTRAINT FK_refunds_event_applications FOREIGN KEY (application_id) REFERENCES dbo.event_applications(id),
    CONSTRAINT FK_refunds_payments FOREIGN KEY (payment_id) REFERENCES dbo.payments(id),
    CONSTRAINT CK_refunds_refund_status CHECK (refund_status IN (N'REFUND_REQUESTED', N'REFUNDING', N'REFUND_FAILED', N'REFUNDED'))
);
GO

CREATE UNIQUE INDEX UX_refunds_application ON dbo.refunds(application_id);
CREATE INDEX IX_refunds_payment ON dbo.refunds(payment_id);
CREATE INDEX IX_refunds_refund_status ON dbo.refunds(refund_status);
GO

CREATE TABLE dbo.notifications
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NOT NULL,
    category NVARCHAR(30) NOT NULL,
    type NVARCHAR(50) NOT NULL,
    target_type NVARCHAR(30) NOT NULL,
    target_id BIGINT NULL,
    dedup_key NVARCHAR(255) NULL,
    title NVARCHAR(150) NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    is_read BIT NOT NULL CONSTRAINT DF_notifications_is_read DEFAULT 0,
    read_at DATETIME2(0) NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_notifications_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_notifications PRIMARY KEY (id),
    CONSTRAINT FK_notifications_users FOREIGN KEY (user_id) REFERENCES dbo.users(id),
    CONSTRAINT CK_notifications_category CHECK (category IN (
        N'APPLICATION_REVIEW',
        N'REGISTRATION',
        N'PAYMENT',
        N'STALL_ASSIGNMENT',
        N'EVENT_CHANGE',
        N'ORGANIZER_MANAGEMENT',
        N'EVENT_MANAGEMENT',
        N'SYSTEM',
        N'EXCEPTION'
    )),
    CONSTRAINT CK_notifications_target_type CHECK (target_type IN (
        N'EVENT_APPLICATION',
        N'MARKET_EVENT',
        N'USER',
        N'ORGANIZER_PROFILE',
        N'PAYMENT',
        N'REFUND',
        N'EVENT_UNPUBLISH_REQUEST',
        N'SYSTEM'
    )),
    CONSTRAINT CK_notifications_target_reference CHECK (
        (target_type = N'SYSTEM' AND target_id IS NULL)
        OR (target_type <> N'SYSTEM' AND target_id IS NOT NULL)
    ),
    CONSTRAINT CK_notifications_read_state CHECK (
        (is_read = 0 AND read_at IS NULL)
        OR (is_read = 1 AND read_at IS NOT NULL)
    )
);
GO

CREATE INDEX IX_notifications_user_created
ON dbo.notifications(user_id, created_at DESC, id DESC);

CREATE INDEX IX_notifications_user_is_read_created
ON dbo.notifications(user_id, is_read, created_at DESC, id DESC);

CREATE INDEX IX_notifications_user_category_is_read_created
ON dbo.notifications(user_id, category, is_read, created_at DESC, id DESC);

CREATE UNIQUE INDEX UX_notifications_dedup_key
ON dbo.notifications(dedup_key)
WHERE dedup_key IS NOT NULL;
GO

CREATE TABLE dbo.admin_operation_logs
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    admin_user_id BIGINT NOT NULL,
    operation_type NVARCHAR(30) NOT NULL,
    target_type NVARCHAR(30) NOT NULL,
    target_id BIGINT NULL,
    target_label NVARCHAR(200) NOT NULL,
    action_content NVARCHAR(300) NOT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_admin_operation_logs_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_admin_operation_logs PRIMARY KEY (id),
    CONSTRAINT FK_admin_operation_logs_admin_user FOREIGN KEY (admin_user_id) REFERENCES dbo.users(id),
    CONSTRAINT CK_admin_operation_logs_operation_type CHECK (operation_type IN (
        N'ACTIVITY_REVIEW',
        N'REQUEST_REVISION',
        N'ACCOUNT_DISABLED',
        N'ACCOUNT_RESTORED',
        N'EVENT_UNPUBLISH_REVIEW',
        N'MAP_BUILD_COMPLETED',
        N'SYSTEM_SETTING'
    )),
    CONSTRAINT CK_admin_operation_logs_target_type CHECK (target_type IN (
        N'USER',
        N'MARKET_EVENT',
        N'EVENT_UNPUBLISH_REQUEST',
        N'SYSTEM_SETTING'
    ))
);
GO

CREATE INDEX IX_admin_operation_logs_created ON dbo.admin_operation_logs(created_at);
CREATE INDEX IX_admin_operation_logs_type_created ON dbo.admin_operation_logs(operation_type, created_at);
CREATE INDEX IX_admin_operation_logs_admin_created ON dbo.admin_operation_logs(admin_user_id, created_at);
CREATE INDEX IX_admin_operation_logs_target ON dbo.admin_operation_logs(target_type, target_id);
GO

CREATE TABLE dbo.request_logs
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    user_id BIGINT NULL,
    method NVARCHAR(10) NOT NULL,
    path NVARCHAR(500) NOT NULL,
    status_code INT NULL,
    created_at DATETIME2(0) NOT NULL CONSTRAINT DF_request_logs_created_at DEFAULT SYSDATETIME(),
    CONSTRAINT PK_request_logs PRIMARY KEY (id),
    CONSTRAINT FK_request_logs_users FOREIGN KEY (user_id) REFERENCES dbo.users(id)
);
GO

CREATE INDEX IX_request_logs_user_created ON dbo.request_logs(user_id, created_at);
CREATE INDEX IX_request_logs_path_created ON dbo.request_logs(path, created_at);
GO

CREATE TABLE dbo.status_logs
(
    id BIGINT IDENTITY(1,1) NOT NULL,
    request_log_id BIGINT NOT NULL,
    target_type NVARCHAR(100) NOT NULL,
    target_id BIGINT NOT NULL,
    status_field NVARCHAR(100) NOT NULL,
    new_status NVARCHAR(100) NOT NULL,
    CONSTRAINT PK_status_logs PRIMARY KEY (id),
    CONSTRAINT FK_status_logs_request_logs FOREIGN KEY (request_log_id) REFERENCES dbo.request_logs(id)
);
GO

CREATE INDEX IX_status_logs_request_log ON dbo.status_logs(request_log_id);
CREATE INDEX IX_status_logs_target ON dbo.status_logs(target_type, target_id);
CREATE INDEX IX_status_logs_field ON dbo.status_logs(status_field);
GO

/* =========================================================
   Column descriptions
   ========================================================= */

CREATE OR ALTER PROCEDURE dbo.usp_add_table_description
    @table_name SYSNAME,
    @description NVARCHAR(4000)
AS
BEGIN
    IF EXISTS (
        SELECT 1
    FROM sys.extended_properties ep
        INNER JOIN sys.tables t ON ep.major_id = t.object_id
        INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
    WHERE ep.name = N'MS_Description'
        AND s.name = N'dbo'
        AND t.name = @table_name
        AND ep.minor_id = 0
    )
    BEGIN
        EXEC sys.sp_updateextendedproperty
            @name = N'MS_Description',
            @value = @description,
            @level0type = N'SCHEMA', @level0name = N'dbo',
            @level1type = N'TABLE',  @level1name = @table_name;
    END
    ELSE
    BEGIN
        EXEC sys.sp_addextendedproperty
            @name = N'MS_Description',
            @value = @description,
            @level0type = N'SCHEMA', @level0name = N'dbo',
            @level1type = N'TABLE',  @level1name = @table_name;
    END
END
GO

CREATE OR ALTER PROCEDURE dbo.usp_add_column_description
    @table_name SYSNAME,
    @column_name SYSNAME,
    @description NVARCHAR(4000)
AS
BEGIN
    IF EXISTS (
        SELECT 1
    FROM sys.extended_properties ep
        INNER JOIN sys.tables t ON ep.major_id = t.object_id
        INNER JOIN sys.schemas s ON t.schema_id = s.schema_id
        INNER JOIN sys.columns c ON ep.major_id = c.object_id AND ep.minor_id = c.column_id
    WHERE ep.name = N'MS_Description'
        AND s.name = N'dbo'
        AND t.name = @table_name
        AND c.name = @column_name
    )
    BEGIN
        EXEC sys.sp_updateextendedproperty
            @name = N'MS_Description',
            @value = @description,
            @level0type = N'SCHEMA', @level0name = N'dbo',
            @level1type = N'TABLE',  @level1name = @table_name,
            @level2type = N'COLUMN', @level2name = @column_name;
    END
    ELSE
    BEGIN
        EXEC sys.sp_addextendedproperty
            @name = N'MS_Description',
            @value = @description,
            @level0type = N'SCHEMA', @level0name = N'dbo',
            @level1type = N'TABLE',  @level1name = @table_name,
            @level2type = N'COLUMN', @level2name = @column_name;
    END
END
GO

EXEC dbo.usp_add_column_description N'users', N'id', N'使用者主鍵';
EXEC dbo.usp_add_column_description N'users', N'role', N'帳號角色（VENDOR/ORGANIZER/ADMIN）';
EXEC dbo.usp_add_column_description N'users', N'email', N'登入 Email';
EXEC dbo.usp_add_column_description N'users', N'password_hash', N'密碼雜湊值（LOCAL）';
EXEC dbo.usp_add_column_description N'users', N'provider', N'登入來源（LOCAL/GOOGLE/BOTH）';
EXEC dbo.usp_add_column_description N'users', N'google_sub', N'Google 帳號唯一識別碼（OIDC subject），GOOGLE/BOTH 必填';
EXEC dbo.usp_add_column_description N'users', N'status', N'帳號狀態（UNACTIVE/ACTIVE/DISABLED/IS_DELETED）';
EXEC dbo.usp_add_column_description N'users', N'isLogin', N'是否已有登入中的裝置';
EXEC dbo.usp_add_column_description N'users', N'email_verified_at', N'Email 驗證完成時間';
EXEC dbo.usp_add_column_description N'users', N'created_at', N'建立時間';
EXEC dbo.usp_add_column_description N'users', N'expired_time', N'自動登出判斷時間';
EXEC dbo.usp_add_column_description N'users', N'updated_at', N'更新時間';

EXEC dbo.usp_add_column_description N'user_tokens', N'id', N'使用者 token ID';
EXEC dbo.usp_add_column_description N'user_tokens', N'user_id', N'使用者 ID';
EXEC dbo.usp_add_column_description N'user_tokens', N'token', N'token';
EXEC dbo.usp_add_column_description N'user_tokens', N'token_type', N'token 類型（EMAIL_VERIFY/PASSWORD_RESET）';
EXEC dbo.usp_add_column_description N'user_tokens', N'expires_at', N'過期時間';

EXEC dbo.usp_add_column_description N'categories', N'id', N'分類 ID';
EXEC dbo.usp_add_column_description N'categories', N'name', N'分類名稱';
EXEC dbo.usp_add_column_description N'categories', N'slug', N'分類代碼';
EXEC dbo.usp_add_column_description N'categories', N'is_active', N'是否啟用';

EXEC dbo.usp_add_column_description N'user_profiles', N'id', N'個人資料 ID';
EXEC dbo.usp_add_column_description N'user_profiles', N'user_id', N'使用者 ID';
EXEC dbo.usp_add_column_description N'user_profiles', N'profile_type', N'資料類型（VENDOR/ORGANIZER）';
EXEC dbo.usp_add_column_description N'user_profiles', N'contact_name', N'聯絡人';
EXEC dbo.usp_add_column_description N'user_profiles', N'contact_phone', N'聯絡電話';
EXEC dbo.usp_add_column_description N'user_profiles', N'contact_email', N'聯絡 Email';
EXEC dbo.usp_add_column_description N'user_profiles', N'city', N'縣市';
EXEC dbo.usp_add_column_description N'user_profiles', N'district', N'區';
EXEC dbo.usp_add_column_description N'user_profiles', N'address', N'詳細地址';

EXEC dbo.usp_add_column_description N'vendor_profiles', N'id', N'攤主品牌資料 ID';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'user_profile_id', N'共用個人資料 ID';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'category_id', N'品牌單一分類 ID';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'brand_name', N'品牌名稱';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'avatar_image_url', N'攤主頭像圖片路徑';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'cover_image_url', N'攤主封面圖片路徑';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'instagram_url', N'Instagram';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'facebook_url', N'Facebook';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'website_url', N'官方網站';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'brand_summary', N'品牌簡述';
EXEC dbo.usp_add_column_description N'vendor_profiles', N'brand_description', N'品牌介紹';

EXEC dbo.usp_add_column_description N'organizer_profiles', N'id', N'主辦方資料 ID';
EXEC dbo.usp_add_column_description N'organizer_profiles', N'user_profile_id', N'共用個人資料 ID';
EXEC dbo.usp_add_column_description N'organizer_profiles', N'organizer_name', N'主辦方名稱';
EXEC dbo.usp_add_column_description N'organizer_profiles', N'company_name', N'公司名稱';
EXEC dbo.usp_add_column_description N'organizer_profiles', N'tax_id', N'統一編號';
EXEC dbo.usp_add_column_description N'organizer_profiles', N'service_days', N'服務星期';
EXEC dbo.usp_add_column_description N'organizer_profiles', N'service_start_time', N'服務開始時間';
EXEC dbo.usp_add_column_description N'organizer_profiles', N'service_end_time', N'服務結束時間';

EXEC dbo.usp_add_column_description N'admin_profiles', N'id', N'管理員資料 ID';
EXEC dbo.usp_add_column_description N'admin_profiles', N'user_id', N'管理員使用者 ID';
EXEC dbo.usp_add_column_description N'admin_profiles', N'admin_name', N'管理員顯示名稱';

EXEC dbo.usp_add_column_description N'vendor_products', N'id', N'商品 ID';
EXEC dbo.usp_add_column_description N'vendor_products', N'vendor_profile_id', N'攤主品牌資料 ID';
EXEC dbo.usp_add_column_description N'vendor_products', N'name', N'商品名稱';
EXEC dbo.usp_add_column_description N'vendor_products', N'short_description', N'商品簡介';
EXEC dbo.usp_add_column_description N'vendor_products', N'description', N'商品介紹';
EXEC dbo.usp_add_column_description N'vendor_products', N'price', N'商品價格';
EXEC dbo.usp_add_column_description N'vendor_products', N'image_url', N'商品圖片';

EXEC dbo.usp_add_column_description N'market_events', N'id', N'活動 ID';
EXEC dbo.usp_add_column_description N'market_events', N'user_id', N'主辦方 ID';
EXEC dbo.usp_add_column_description N'market_events', N'title', N'活動名稱';
EXEC dbo.usp_add_column_description N'market_events', N'summary', N'活動摘要';
EXEC dbo.usp_add_column_description N'market_events', N'description', N'活動介紹';
EXEC dbo.usp_add_column_description N'market_events', N'location_name', N'地點名稱';
EXEC dbo.usp_add_column_description N'market_events', N'city', N'縣市';
EXEC dbo.usp_add_column_description N'market_events', N'district', N'地區';
EXEC dbo.usp_add_column_description N'market_events', N'address', N'地址';
EXEC dbo.usp_add_column_description N'market_events', N'notice', N'活動注意事項';
EXEC dbo.usp_add_column_description N'market_events', N'start_at', N'活動開始日期時間';
EXEC dbo.usp_add_column_description N'market_events', N'end_at', N'活動結束日期時間';
EXEC dbo.usp_add_column_description N'market_events', N'registration_start_at', N'報名開始時間';
EXEC dbo.usp_add_column_description N'market_events', N'registration_end_at', N'報名截止時間';
EXEC dbo.usp_add_column_description N'market_events', N'max_booths', N'攤位總數';
EXEC dbo.usp_add_column_description N'market_events', N'stall_width', N'本活動固定攤位寬度';
EXEC dbo.usp_add_column_description N'market_events', N'stall_length', N'本活動固定攤位長度';
EXEC dbo.usp_add_column_description N'market_events', N'base_fee', N'基本攤位費';
EXEC dbo.usp_add_column_description N'market_events', N'deposit_amount', N'每攤保證金';
EXEC dbo.usp_add_column_description N'market_events', N'traffic_info_driving', N'開車交通資訊';
EXEC dbo.usp_add_column_description N'market_events', N'traffic_info_bus', N'公車交通資訊';
EXEC dbo.usp_add_column_description N'market_events', N'traffic_info_metro', N'捷運交通資訊';
EXEC dbo.usp_add_column_description N'market_events', N'cover_image_url', N'活動封面';
EXEC dbo.usp_add_column_description N'market_events', N'map_image_url', N'攤位地圖底圖';
EXEC dbo.usp_add_column_description N'market_events', N'public_info_at', N'公開資訊時間';
EXEC dbo.usp_add_column_description N'market_events', N'brands_public_at', N'品牌名單公開時間';
EXEC dbo.usp_add_column_description N'market_events', N'workflow_status', N'活動流程狀態（DRAFT/PENDING_REVIEW/REVISION_REQUIRED/MAP_BUILDING/READY_TO_PUBLISH/PUBLISHED/FINAL_REVIEW/UNPUBLISH_REQUESTED/UNPUBLISHED/CANCELLED）';
EXEC dbo.usp_add_column_description N'market_events', N'review_note', N'補件原因 / 審核備註';
EXEC dbo.usp_add_column_description N'market_events', N'create_at', N'活動建立時間';

EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'id', N'活動下架申請 ID';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'event_id', N'活動 ID';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'requested_by', N'提出下架申請的主辦方使用者 ID';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'reviewed_by', N'審核下架申請的管理員使用者 ID';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'reason', N'下架申請原因';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'status', N'下架申請狀態（PENDING/APPROVED/REJECTED/CANCELLED）';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'review_note', N'管理員審核備註';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'requested_at', N'下架申請時間';
EXEC dbo.usp_add_column_description N'event_unpublish_requests', N'reviewed_at', N'下架審核時間';

EXEC dbo.usp_add_column_description N'event_stall_zones', N'id', N'分區 ID';
EXEC dbo.usp_add_column_description N'event_stall_zones', N'event_id', N'活動 ID';
EXEC dbo.usp_add_column_description N'event_stall_zones', N'zone_name', N'分區名稱';
EXEC dbo.usp_add_column_description N'event_stall_zones', N'zone_color', N'分區顏色（例如 #FF8A00）';
EXEC dbo.usp_add_column_description N'event_stall_zones', N'stall_count', N'分區攤位數量';

EXEC dbo.usp_add_column_description N'event_stalls', N'id', N'攤位 ID';
EXEC dbo.usp_add_column_description N'event_stalls', N'event_id', N'活動 ID';
EXEC dbo.usp_add_column_description N'event_stalls', N'zone_id', N'分區 ID';
EXEC dbo.usp_add_column_description N'event_stalls', N'stall_no', N'攤位編號';
EXEC dbo.usp_add_column_description N'event_stalls', N'status', N'攤位狀態（AVAILABLE/SELECTED/ASSIGNED/DISABLED）';

EXEC dbo.usp_add_column_description N'event_equipments', N'id', N'活動設備 ID';
EXEC dbo.usp_add_column_description N'event_equipments', N'event_id', N'活動 ID';
EXEC dbo.usp_add_column_description N'event_equipments', N'equipment_group_key', N'設備群組識別，用於合併同一品項的免費與付費設定';
EXEC dbo.usp_add_column_description N'event_equipments', N'name', N'設備名稱';
EXEC dbo.usp_add_column_description N'event_equipments', N'rental_fee', N'租金';
EXEC dbo.usp_add_column_description N'event_equipments', N'pricing_unit', N'時間計費方式：HOUR/DAY';
EXEC dbo.usp_add_column_description N'event_equipments', N'unit', N'租借單位名稱，例如：張、頂、組、條；EQUIPMENT 必填，POWER 為 NULL';
EXEC dbo.usp_add_column_description N'event_equipments', N'charge_type', N'收費類型（FREE/PAID）';
EXEC dbo.usp_add_column_description N'event_equipments', N'item_type', N'項目類型（EQUIPMENT/POWER）';
EXEC dbo.usp_add_column_description N'event_equipments', N'description', N'設備詳細資訊';
EXEC dbo.usp_add_column_description N'event_equipments', N'stock_quantity', N'可租借數量';
EXEC dbo.usp_add_column_description N'event_equipments', N'per_stall_rental_limit', N'單一攤位租借上限';
EXEC dbo.usp_add_column_description N'event_equipments', N'rental_status', N'租借狀態（ACTIVE/UNACTIVE）';
EXEC dbo.usp_add_column_description N'event_equipments', N'wattage_limit', N'電力設備瓦數上限';

EXEC dbo.usp_add_column_description N'event_applications', N'id', N'報名 ID';
EXEC dbo.usp_add_column_description N'event_applications', N'application_no', N'報名編號';
EXEC dbo.usp_add_column_description N'event_applications', N'event_id', N'活動 ID';
EXEC dbo.usp_add_column_description N'event_applications', N'user_id', N'攤主 ID';
EXEC dbo.usp_add_column_description N'event_applications', N'vendor_profile_id', N'攤主品牌資料 ID';
EXEC dbo.usp_add_column_description N'event_applications', N'vehicle_no', N'車牌';
EXEC dbo.usp_add_column_description N'event_applications', N'applicant_note', N'攤主備註';
EXEC dbo.usp_add_column_description N'event_applications', N'total_amount', N'試算總金額';
EXEC dbo.usp_add_column_description N'event_applications', N'deposit_amount', N'保證金金額';
EXEC dbo.usp_add_column_description N'event_applications', N'deposit_status', N'保證金退還狀態（NOT_RETURNED/RETURNED）';
EXEC dbo.usp_add_column_description N'event_applications', N'payment_due_at', N'付款期限';
EXEC dbo.usp_add_column_description N'event_applications', N'review_status', N'報名審核狀態（PENDING/APPROVED/REJECTED）';
EXEC dbo.usp_add_column_description N'event_applications', N'payment_status', N'報名付款狀態（PENDING/PAID/FAILED/EXPIRED）';
EXEC dbo.usp_add_column_description N'event_applications', N'is_cancelled', N'報名是否取消';
EXEC dbo.usp_add_column_description N'event_applications', N'created_at', N'報名建立時間';

EXEC dbo.usp_add_column_description N'application_review_notes', N'id', N'報名審核備註 ID';
EXEC dbo.usp_add_column_description N'application_review_notes', N'application_id', N'報名 ID';
EXEC dbo.usp_add_column_description N'application_review_notes', N'review_note', N'報名審核未通過原因';
EXEC dbo.usp_add_column_description N'application_review_notes', N'review_note_detail', N'報名審核未通過原因詳細說明';
EXEC dbo.usp_add_column_description N'application_review_notes', N'created_at', N'建立時間';

EXEC dbo.usp_add_column_description N'equipment_rentals', N'id', N'設備租借 ID';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'application_id', N'報名 ID';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'event_equipment_id', N'活動設備 ID';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'equipment_name', N'租借時設備名稱';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'rental_fee', N'租借時單價';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'pricing_unit', N'租借時計費方式：HOUR/DAY';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'unit', N'租借時單位名稱快照，例如：張、頂、組、條';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'quantity', N'租借數量';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'rental_units', N'計費單位數';
EXEC dbo.usp_add_column_description N'equipment_rentals', N'subtotal', N'租借小計';

EXEC dbo.usp_add_column_description N'rental_appliances', N'id', N'租借電器 ID';
EXEC dbo.usp_add_column_description N'rental_appliances', N'equipment_rental_id', N'設備租借 ID';
EXEC dbo.usp_add_column_description N'rental_appliances', N'appliance_name', N'電器名稱';
EXEC dbo.usp_add_column_description N'rental_appliances', N'wattage', N'電器瓦數';

EXEC dbo.usp_add_column_description N'application_dates', N'id', N'ID';
EXEC dbo.usp_add_column_description N'application_dates', N'application_id', N'報名 ID';
EXEC dbo.usp_add_column_description N'application_dates', N'apply_date', N'報名參加日期';
EXEC dbo.usp_add_column_description N'application_dates', N'selected_stall_id', N'報名日期選擇的活動攤位';

EXEC dbo.usp_add_column_description N'payments', N'id', N'付款 ID';
EXEC dbo.usp_add_column_description N'payments', N'payment_no', N'藍新的 MerchantOrderNo';
EXEC dbo.usp_add_column_description N'payments', N'application_id', N'報名 ID';
EXEC dbo.usp_add_column_description N'payments', N'amount', N'付款金額';
EXEC dbo.usp_add_column_description N'payments', N'provider', N'金流服務商';
EXEC dbo.usp_add_column_description N'payments', N'provider_trade_no', N'金流交易編號';
EXEC dbo.usp_add_column_description N'payments', N'provider_response_code', N'金流服務商回應代碼';
EXEC dbo.usp_add_column_description N'payments', N'provider_message', N'金流服務商回應訊息';
EXEC dbo.usp_add_column_description N'payments', N'status', N'付款狀態（PENDING/PAID/FAILED/EXPIRED）';
EXEC dbo.usp_add_column_description N'payments', N'paid_at', N'付款成功時間';
EXEC dbo.usp_add_column_description N'payments', N'created_at', N'建立時間';

EXEC dbo.usp_add_column_description N'refunds', N'id', N'退款 ID';
EXEC dbo.usp_add_column_description N'refunds', N'refund_no', N'藍新退款交易序號';
EXEC dbo.usp_add_column_description N'refunds', N'application_id', N'報名 ID';
EXEC dbo.usp_add_column_description N'refunds', N'payment_id', N'原付款紀錄';
EXEC dbo.usp_add_column_description N'refunds', N'amount', N'退款金額';
EXEC dbo.usp_add_column_description N'refunds', N'reason', N'退款原因';
EXEC dbo.usp_add_column_description N'refunds', N'failed_reason', N'退款失敗原因';
EXEC dbo.usp_add_column_description N'refunds', N'refund_status', N'退款處理狀態（REFUND_REQUESTED/REFUNDING/REFUND_FAILED/REFUNDED）';
EXEC dbo.usp_add_column_description N'refunds', N'refunded_at', N'實際退款完成時間';

EXEC dbo.usp_add_column_description N'notifications', N'id', N'通知 ID';
EXEC dbo.usp_add_column_description N'notifications', N'user_id', N'接收者';
EXEC dbo.usp_add_column_description N'notifications', N'category', N'通知中心分類（攤主、主辦方及管理員共用）';
EXEC dbo.usp_add_column_description N'notifications', N'type', N'通知事件類型，例如 APPLICATION_APPROVED/PAYMENT_PAID/EVENT_UPDATED';
EXEC dbo.usp_add_column_description N'notifications', N'target_type', N'通知關聯對象類型（EVENT_APPLICATION/MARKET_EVENT/USER/ORGANIZER_PROFILE/PAYMENT/REFUND/SYSTEM）';
EXEC dbo.usp_add_column_description N'notifications', N'target_id', N'通知關聯資料 ID；SYSTEM 類型為 NULL';
EXEC dbo.usp_add_column_description N'notifications', N'title', N'通知標題';
EXEC dbo.usp_add_column_description N'notifications', N'content', N'通知內容';
EXEC dbo.usp_add_column_description N'notifications', N'is_read', N'是否已讀';
EXEC dbo.usp_add_column_description N'notifications', N'read_at', N'閱讀時間';
EXEC dbo.usp_add_column_description N'notifications', N'created_at', N'通知建立時間';

EXEC dbo.usp_add_column_description N'admin_operation_logs', N'id', N'管理員操作紀錄 ID';
EXEC dbo.usp_add_column_description N'admin_operation_logs', N'admin_user_id', N'執行操作的管理員使用者 ID';
EXEC dbo.usp_add_column_description N'admin_operation_logs', N'operation_type', N'操作類型（ACTIVITY_REVIEW/REQUEST_REVISION/ACCOUNT_DISABLED/ACCOUNT_RESTORED/EVENT_UNPUBLISH_REVIEW/MAP_BUILD_COMPLETED/SYSTEM_SETTING）';
EXEC dbo.usp_add_column_description N'admin_operation_logs', N'target_type', N'操作對象類型（USER/MARKET_EVENT/EVENT_UNPUBLISH_REQUEST/SYSTEM_SETTING）';
EXEC dbo.usp_add_column_description N'admin_operation_logs', N'target_id', N'操作對象 ID';
EXEC dbo.usp_add_column_description N'admin_operation_logs', N'target_label', N'操作對象顯示名稱快照';
EXEC dbo.usp_add_column_description N'admin_operation_logs', N'action_content', N'操作內容顯示文字';
EXEC dbo.usp_add_column_description N'admin_operation_logs', N'created_at', N'操作時間';

EXEC dbo.usp_add_column_description N'request_logs', N'id', N'請求紀錄 ID';
EXEC dbo.usp_add_column_description N'request_logs', N'user_id', N'發送請求者';
EXEC dbo.usp_add_column_description N'request_logs', N'method', N'HTTP 方法';
EXEC dbo.usp_add_column_description N'request_logs', N'path', N'API 路徑';
EXEC dbo.usp_add_column_description N'request_logs', N'status_code', N'回應狀態碼';
EXEC dbo.usp_add_column_description N'request_logs', N'created_at', N'建立時間';

EXEC dbo.usp_add_column_description N'status_logs', N'id', N'狀態紀錄 ID';
EXEC dbo.usp_add_column_description N'status_logs', N'request_log_id', N'對應的 API 請求紀錄 ID';
EXEC dbo.usp_add_column_description N'status_logs', N'target_type', N'狀態來源類型，例如 APPLICATION/PAYMENT/REFUND/STALL/USER/EVENT';
EXEC dbo.usp_add_column_description N'status_logs', N'target_id', N'狀態來源資料 ID';
EXEC dbo.usp_add_column_description N'status_logs', N'status_field', N'狀態欄位名稱';
EXEC dbo.usp_add_column_description N'status_logs', N'new_status', N'更新後狀態值';

GO

DROP PROCEDURE dbo.usp_add_column_description;
GO

DROP PROCEDURE dbo.usp_add_table_description;
GO

IF NOT EXISTS (
    SELECT 1
    FROM dbo.users
    WHERE email = 'admin@marketday.local'
)
BEGIN
    INSERT INTO dbo.users (
        role,
        email,
        password_hash,
        provider,
        status,
        isLogin,
        email_verified_at
    )
    VALUES (
        'ADMIN',
        'admin@marketday.local',
        '$2a$10$jZ5SinwJD56C7PQX8Kj2wORspIPscc5H9Nf9nE1RcKj1j7/.o4QqW',
        'LOCAL',
        'ACTIVE',
        0,
        SYSDATETIME()
    );
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM dbo.admin_profiles ap
    INNER JOIN dbo.users u ON u.id = ap.user_id
    WHERE u.email = 'admin@marketday.local'
)
BEGIN
    INSERT INTO dbo.admin_profiles (user_id, admin_name)
    SELECT id, N'系統管理員'
    FROM dbo.users
    WHERE email = 'admin@marketday.local';
END
GO

/* =========================================================
   Default category seed data
   ========================================================= */

INSERT INTO dbo.categories (name, slug, is_active)
SELECT N'餐飲美食', N'food', 1
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.categories WHERE slug = N'food'
);

INSERT INTO dbo.categories (name, slug, is_active)
SELECT N'文創手作', N'handmade', 1
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.categories WHERE slug = N'handmade'
);

INSERT INTO dbo.categories (name, slug, is_active)
SELECT N'親子家庭', N'family', 1
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.categories WHERE slug = N'family'
);

INSERT INTO dbo.categories (name, slug, is_active)
SELECT N'寵物生活', N'pet-life', 1
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.categories WHERE slug = N'pet-life'
);

INSERT INTO dbo.categories (name, slug, is_active)
SELECT N'植物選物', N'plants', 1
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.categories WHERE slug = N'plants'
);

INSERT INTO dbo.categories (name, slug, is_active)
SELECT N'服飾配件', N'fashion-accessories', 1
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.categories WHERE slug = N'fashion-accessories'
);

INSERT INTO dbo.categories (name, slug, is_active)
SELECT N'玩具選物', N'toys', 1
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.categories WHERE slug = N'toys'
);
GO
