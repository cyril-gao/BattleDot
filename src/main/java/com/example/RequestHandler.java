package com.example;

import java.util.function.Function;
import org.json.JSONObject;

public interface RequestHandler extends Function<JSONObject, Boolean> {}
