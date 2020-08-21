package fi.thl.covid19.publishtoken.error;


import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;

@RestController
@RequestMapping("/test")
public class TestController {

    public static final String FAILURE_STRING = "TEST_FAILURE_STRING";

    @GetMapping("/input-validation-failure")
    public void getInputValidationException() {
        throw new InputValidationException(FAILURE_STRING);
    }

    @GetMapping("/illegal-state")
    public void getIllegalStateException() {
        throw new IllegalStateException(FAILURE_STRING);
    }

    @GetMapping("/illegal-argument")
    public void getIllegalArgumentException() {
        throw new IllegalArgumentException(FAILURE_STRING);
    }

    @GetMapping("/sql-exception")
    public void getSqlException() throws SQLException {
        throw new SQLException(FAILURE_STRING);
    }

    @GetMapping("/should-be-int-path/{int_value}")
    public void getIntPath(@PathVariable(value = "int_value") Integer integer) {
        if (integer == null) throw new NullPointerException("Should get parameter");
    }

    @GetMapping("/should-be-int-param")
    public void getIntParam(@RequestParam(value = "param") Integer integer) {
        if (integer == null) throw new NullPointerException("Should get parameter");
    }
}
