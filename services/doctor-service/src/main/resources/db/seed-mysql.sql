-- =====================================================================
-- doctor-service 种子数据（MySQL / docker profile 专用，幂等写法）
--
-- 容器反复重启而数据卷一直保留，「不存在才插入」避免医生越重启越多。
-- 执行时机：Hibernate 建表之后（defer-datasource-initialization: true）
-- =====================================================================
INSERT INTO doctor (name, specialty, daily_capacity)
SELECT '李医生', '内科', 4 FROM (SELECT 1) AS seed
WHERE NOT EXISTS (SELECT 1 FROM doctor WHERE name = '李医生');

INSERT INTO doctor (name, specialty, daily_capacity)
SELECT '王医生', '外科', 3 FROM (SELECT 1) AS seed
WHERE NOT EXISTS (SELECT 1 FROM doctor WHERE name = '王医生');

INSERT INTO doctor (name, specialty, daily_capacity)
SELECT '赵医生', '影像', 6 FROM (SELECT 1) AS seed
WHERE NOT EXISTS (SELECT 1 FROM doctor WHERE name = '赵医生');
