-- Create payment database if it doesn't exist
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_database WHERE datname = 'payment') THEN
    CREATE DATABASE payment;
  END IF;
END
$$;

