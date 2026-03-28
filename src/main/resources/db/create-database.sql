/*
 * Firebird Database Creation Script for Frodo
 * 
 * Prerequisites:
 * - Firebird 5.0+ installed
 * - SYSDBA credentials (default: sysdba/masterkey)
 * 
 * Usage:
 *   isql -user sysdba -password masterkey -input create-database.sql
 * 
 * Or connect via isql and copy-paste the CREATE DATABASE statement.
 */

-- Create database with UTF-8 character set and 32K page size
-- 32K page size is optimal for tables with large VARCHAR columns
-- UTF-8 character set ensures proper international character support
CREATE DATABASE 'localhost:frodo.fdb'
  PAGE_SIZE 32768
  DEFAULT CHARACTER SET UTF8
  USER 'sysdba'
  PASSWORD 'masterkey';

-- Verify configuration
-- Expected: PAGE_SIZE=32768, DEFAULT_CHARSET=UTF8
SELECT
  MON$PAGE_SIZE AS PAGE_SIZE,
  RDB$CHARACTER_SET_NAME AS DEFAULT_CHARSET
FROM MON$DATABASE
CROSS JOIN RDB$DATABASE;

-- Show database info
SHOW DATABASE;

QUIT;
