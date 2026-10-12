-- =====================================================================
-- 基础种子数据：医生（号源提供方）
--
-- 用途：
--   1) H2 演示/测试环境：应用启动或测试运行时自动执行（内嵌数据库默认生效）
--   2) MySQL 环境：手工执行一次即可
--      mysql -u root -p petclinic < monolith/src/main/resources/data.sql
--
-- 说明：id 由数据库自增生成，无需指定。
-- =====================================================================

INSERT INTO doctor (name, specialty, daily_capacity) VALUES ('李医生', '内科', 4);
INSERT INTO doctor (name, specialty, daily_capacity) VALUES ('王医生', '外科', 3);
INSERT INTO doctor (name, specialty, daily_capacity) VALUES ('赵医生', '影像', 6);
