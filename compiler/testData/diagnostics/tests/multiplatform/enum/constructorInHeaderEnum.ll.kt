// MODULE: m1-common
// FILE: common.kt

<!NO_ACTUAL_FOR_EXPECT!>expect enum class En<!EXPECTED_ENUM_CONSTRUCTOR!>(x: Int)<!> {
    E1,
    E2<!SUPERTYPE_INITIALIZED_IN_EXPECTED_CLASS!>(42)<!>,
    ;

    <!EXPECTED_ENUM_CONSTRUCTOR!>constructor(s: String)<!>
}<!>

<!NO_ACTUAL_FOR_EXPECT!>expect enum class En2 {
    E1<!SUPERTYPE_INITIALIZED_IN_EXPECTED_CLASS!>()<!>
}<!>
