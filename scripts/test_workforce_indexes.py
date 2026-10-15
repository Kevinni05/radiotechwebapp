import copy
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import Mock

spec = importlib.util.spec_from_file_location("workforce_indexes", Path(__file__).with_name("prepare-workforce-indexes.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class FakeClient:
    def __init__(self, indexes, state="READY"):
        self.indexes = copy.deepcopy(indexes)
        for index in self.indexes:
            index["state"] = state
            index["fields"].append({"fieldPath": "__name__", "order": "ASCENDING"})
        self.created = []

    def list(self, collection):
        return [i for i in self.indexes if i["collectionGroup"] == collection]

    def create(self, index):
        self.created.append(copy.deepcopy(index))
        self.indexes.append({**index, "state": "CREATING"})

    def ready(self, _):
        for index in self.indexes:
            index["state"] = "READY"


class WorkforceIndexesTest(unittest.TestCase):
    def setUp(self):
        self.desired = module.desired_indexes()

    def test_existing_ready_indexes_are_untouched(self):
        client = FakeClient(self.desired)
        self.assertEqual(module.prepare(client, self.desired, apply=True), 0)
        self.assertEqual(client.created, [])

    def test_check_only_does_not_create(self):
        client = FakeClient([])
        self.assertEqual(module.prepare(client, self.desired), 2)
        self.assertEqual(client.created, [])

    def test_apply_creates_only_missing_and_waits_for_readiness(self):
        client = FakeClient(self.desired[:1])
        self.assertEqual(module.prepare(client, self.desired, apply=True, sleep=client.ready), 0)
        self.assertEqual(client.created, self.desired[1:])

    def test_timeout_does_not_report_success_or_recreate(self):
        client = FakeClient([])
        with self.assertRaisesRegex(RuntimeError, "timeout"):
            module.prepare(client, self.desired, apply=True, clock=Mock(side_effect=[0, 601]))
        self.assertEqual(len(client.created), 4)

    def test_repair_state_fails_without_creating_duplicates(self):
        client = FakeClient(self.desired, state="NEEDS_REPAIR")
        with self.assertRaisesRegex(RuntimeError, "repair"):
            module.prepare(client, self.desired, apply=True)
        self.assertEqual(client.created, [])

    def test_paginated_listing(self):
        client = object.__new__(module.Client)
        client.root = "projects/test-project/databases/(default)/collectionGroups/"
        own = copy.deepcopy(self.desired[:2])
        for position, index in enumerate(own):
            index["name"] = client.root + "workforceAttendance/indexes/" + str(position)
        foreign = {**own[0], "name": client.root + "otherCollection/indexes/foreign"}
        client.request = Mock(side_effect=[{"indexes": [foreign, own[0]], "nextPageToken": "a+/="},
                                           {"indexes": [own[1]]}])
        self.assertEqual(client.list("workforceAttendance"), own)
        for call in client.request.call_args_list:
            self.assertNotIn("pageSize", call.args[0])
        self.assertNotIn("?", client.request.call_args_list[0].args[0])
        self.assertIn("pageToken=a%2B%2F%3D", client.request.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
