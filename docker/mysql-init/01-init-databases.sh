#!/bin/bash
# =====================================================================
# MySQL 首次初始化：创建三个服务各自的数据库与账号
#
# 由 MySQL 官方镜像在数据卷为空时自动执行（/docker-entrypoint-initdb.d/），
# 变量来自 compose.yaml 注入的容器环境变量。
#
# 数据归属（最小权限）：
#   pet_user          -> petclinic_pet           （pet-service 独占）
#   doctor_user       -> petclinic_doctor        （doctor-service 独占）
#   appointment_user  -> petclinic_appointment   （appointment-service 独占）
# 每个账号只对自己服务的库有权限，从数据库层面保证「不跨服务读写数据表」。
# =====================================================================
set -euo pipefail

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS petclinic_pet
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS petclinic_doctor
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS petclinic_appointment
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'pet_user'@'%' IDENTIFIED BY '${PET_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'doctor_user'@'%' IDENTIFIED BY '${DOCTOR_DB_PASSWORD}';
CREATE USER IF NOT EXISTS 'appointment_user'@'%' IDENTIFIED BY '${APPOINTMENT_DB_PASSWORD}';

GRANT ALL PRIVILEGES ON petclinic_pet.* TO 'pet_user'@'%';
GRANT ALL PRIVILEGES ON petclinic_doctor.* TO 'doctor_user'@'%';
GRANT ALL PRIVILEGES ON petclinic_appointment.* TO 'appointment_user'@'%';
FLUSH PRIVILEGES;
SQL

echo "[init] 3 databases and 3 users created."
