import sqlite3
import tempfile
import unittest
import warnings
from pathlib import Path

from specgraph_foundry.database import Database


class DatabaseConnectionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.database = Database(
            Path(self.temp.name) / "test.sqlite3"
        )
        self.database.initialize()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_context_manager_closes_connection(
        self,
    ) -> None:
        with self.database.connect() as connection:
            result = connection.execute(
                "SELECT 1"
            ).fetchone()[0]

        self.assertEqual(result, 1)

        with self.assertRaises(
            sqlite3.ProgrammingError
        ):
            connection.execute("SELECT 1")

    def test_repeated_connections_do_not_warn(
        self,
    ) -> None:
        with warnings.catch_warnings():
            warnings.simplefilter(
                "error",
                ResourceWarning,
            )

            for _ in range(100):
                with self.database.connect() as connection:
                    connection.execute(
                        "SELECT 1"
                    ).fetchone()


if __name__ == "__main__":
    unittest.main()
