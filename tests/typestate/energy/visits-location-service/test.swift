import CoreLocation

func test_default() { // (other)
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits() //?VisitsLocationService?error?
}

func test_automotiveNavigation() {
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits()
  locationManager.activityType = CLActivityType.automotiveNavigation //?VisitsLocationService?error?
}

func test_fitness() {
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits()
  locationManager.activityType = CLActivityType.fitness //?VisitsLocationService?error?
}

func test_other() {
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits()
  locationManager.activityType = CLActivityType.other //?VisitsLocationService?error?
}

func test_otherNavigation() {
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits()
  locationManager.activityType = CLActivityType.otherNavigation //?VisitsLocationService?error?
}

func test_reset_good_to_bad() {
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits()
  locationManager.activityType = CLActivityType.airborne
  locationManager.activityType = CLActivityType.fitness //?VisitsLocationService?error?
}

func test_reset_bad_to_good() {
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits()
  locationManager.activityType = CLActivityType.fitness
  locationManager.activityType = CLActivityType.airborne
}

func test_reset_to_default() {
  let locationManager = CLLocationManager()
  locationManager.startMonitoringVisits()
  locationManager.activityType = CLActivityType.airborne
  locationManager.activityType = CLActivityType.other //?VisitsLocationService?error?
}
