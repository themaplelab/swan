protocol Car {
    var intValue : Int? { get set }
}

extension Car {
    public var description: String {
       return self.intValue?.description ?? ""
    }
}

final class HoverCar: Car {
    var intValue: Int? = 1
}


HoverCar().description