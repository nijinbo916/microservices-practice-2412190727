-- =====================================================================
-- 医生种子数据（MySQL / docker profile 专用）
--
-- 与 src/main/resources/data.sql 的区别：
--   data.sql       给 H2 演示与测试用，每次建表后清空重灌，直接 INSERT 即可
--   本文件          给 MySQL 容器用，容器会反复重启而数据卷一直保留，
--                  因此必须写成「不存在才插入」的幂等语句，
--                  否则每重启一次就会多出 3 位医生
--
-- 执行时机：Hibernate 建表之后（defer-datasource-initialization: true）
-- =====================================================================

INSERT INTO doctor (name, specialty, daily_capacity)
SELECT '李医生', '内科', 4 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM doctor WHERE name = '李医生');

INSERT INTO doctor (name, specialty, daily_capacity)
SELECT '王医生', '外科', 3 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM doctor WHERE name = '王医生');

INSERT INTO doctor (name, specialty, daily_capacity)
SELECT '赵医生', '影像', 6 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM doctor WHERE name = '赵医生');
