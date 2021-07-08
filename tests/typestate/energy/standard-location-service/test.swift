import CoreLocation

func test_simple() {
  let locationManager = CLLocationManager()
  locationManager.startUpdatingLocation()
  locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
  locationManager.distanceFilter = 4096 //?StandardLocationService?error?
}
