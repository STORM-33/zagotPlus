import postgres from 'postgres';
import { config } from '../utils/config.js';

export const sql = postgres(config.DATABASE_URL_READONLY, {
  max: 5,
  idle_timeout: 20,
  connect_timeout: 10,
  ssl: 'require',
});