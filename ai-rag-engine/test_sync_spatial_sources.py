import unittest

from sync_spatial_sources import (
    normalize_address_record,
    normalize_admin_boundary_feature,
    normalize_facility_record,
    normalize_parking_restriction_record,
)


class SpatialSourceNormalizationTest(unittest.TestCase):
    def test_normalizes_korean_address_point_columns(self):
        row = {
            "도로명주소": "충청남도 아산시 시민로 456",
            "지번주소": "충청남도 아산시 온천동 1",
            "건물명": "아산시청",
            "위도": "36.789000",
            "경도": "127.001000",
        }

        normalized = normalize_address_record(row)

        self.assertEqual("충청남도 아산시 시민로 456", normalized["road_address"])
        self.assertEqual("아산시청", normalized["building_name"])
        self.assertEqual(36.789, normalized["latitude"])
        self.assertEqual(127.001, normalized["longitude"])

    def test_normalizes_facility_columns_for_any_gis_layer(self):
        row = {
            "시설명": "배방읍 공영주차장",
            "소재지도로명주소": "충청남도 아산시 배방읍 모산로 1",
            "관리기관명": "아산시",
            "위도": "36.774",
            "경도": "127.052",
        }

        normalized = normalize_facility_record(row, "PARKING_LOT")

        self.assertEqual("PARKING_LOT", normalized["facility_type"])
        self.assertEqual("배방읍 공영주차장", normalized["facility_name"])
        self.assertEqual("아산시", normalized["manager"])
        self.assertEqual(36.774, normalized["centroid_lat"])

    def test_normalizes_parking_restriction_columns(self):
        row = {
            "도로명": "온천대로",
            "상세위치": "온양온천역 앞",
            "단속유형": "불법주정차",
            "평일단속시간": "09:00-18:00",
        }

        normalized = normalize_parking_restriction_record(row)

        self.assertEqual("온천대로", normalized["road_name"])
        self.assertEqual("온양온천역 앞", normalized["detail_location"])
        self.assertEqual("불법주정차", normalized["restriction_type"])

    def test_extracts_boundary_bbox_from_geojson_feature(self):
        feature = {
            "properties": {"행정구역코드": "4420010100", "행정구역명": "온천동"},
            "geometry": {
                "type": "Polygon",
                "coordinates": [[[127.0, 36.7], [127.2, 36.7], [127.2, 36.9], [127.0, 36.9], [127.0, 36.7]]],
            },
        }

        normalized = normalize_admin_boundary_feature(feature)

        self.assertEqual("4420010100", normalized["boundary_code"])
        self.assertEqual("온천동", normalized["boundary_name"])
        self.assertEqual(36.7, normalized["min_lat"])
        self.assertEqual(127.2, normalized["max_lon"])


if __name__ == "__main__":
    unittest.main()
