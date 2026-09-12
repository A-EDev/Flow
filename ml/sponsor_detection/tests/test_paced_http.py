from __future__ import annotations

import unittest
from unittest.mock import patch

import requests

from sponsor_detection.data.paced_http import PacedHTTPAdapter


class PacedHTTPAdapterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.now = 0.0
        self.delays = []
        self.adapter = PacedHTTPAdapter(1, clock=lambda: self.now, sleep=self.sleep)

    def sleep(self, delay: float) -> None:
        self.delays.append(delay)
        self.now += delay

    def test_redirect_and_next_video_share_request_pacing(self) -> None:
        calls = []

        def send(request, **kwargs):
            calls.append((self.now, kwargs["timeout"]))
            response = requests.Response()
            response.status_code = 302 if len(calls) == 1 else 200
            response.url = request.url
            response.request = request
            response._content = b"test"
            if response.status_code == 302:
                response.headers["Location"] = "http://example.test/captions"
            return response

        with requests.Session() as session:
            session.mount("https://", self.adapter)
            session.mount("http://", self.adapter)
            with patch("requests.adapters.HTTPAdapter.send", side_effect=send):
                session.get("https://example.test/video")
                session.get("https://example.test/next-video")
        self.assertEqual(calls, [(0, (10, 30)), (1, (10, 30)), (2, (10, 30))])
        self.assertEqual(self.adapter.max_retries.total, 0)

    def test_failed_request_is_not_retried_and_preserves_delay(self) -> None:
        request = requests.Request("GET", "https://example.test").prepare()
        with patch(
            "requests.adapters.HTTPAdapter.send",
            side_effect=requests.Timeout,
        ) as send:
            for _ in range(2):
                with self.assertRaises(requests.Timeout):
                    self.adapter.send(request)
        self.assertEqual(send.call_count, 2)
        self.assertEqual(self.delays, [1])

    def test_existing_idle_time_counts_toward_delay(self) -> None:
        with patch("requests.adapters.HTTPAdapter.send"):
            self.adapter.send(None, timeout=7)
            self.now = 5
            self.adapter.send(None)
        self.assertEqual(self.delays, [])
