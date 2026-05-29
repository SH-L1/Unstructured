import os

import psycopg2
from dotenv import load_dotenv


def get_db_config():
    load_dotenv()

    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": os.getenv("DB_PORT", "5432"),
        "dbname": os.getenv("DB_NAME", "complaintdb"),
        "user": os.getenv("DB_USER", "complaint_user"),
        "password": os.getenv("DB_PASSWORD", "complaint_pass"),
    }


def main():
    config = get_db_config()

    print("[DB 연결 테스트]")
    print(f"- host: {config['host']}")
    print(f"- port: {config['port']}")
    print(f"- dbname: {config['dbname']}")
    print(f"- user: {config['user']}")

    try:
        with psycopg2.connect(**config) as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT NOW();")
                now = cur.fetchone()[0]

        print("\nDB 연결 성공")
        print(f"SELECT NOW() 결과: {now}")
    except Exception as exc:
        print("\nDB 연결 실패")
        print(f"오류: {exc}")
        raise


if __name__ == "__main__":
    main()
