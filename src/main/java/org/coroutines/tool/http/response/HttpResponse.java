package org.coroutines.tool.http.response;

import lombok.*;

/**
 * @Project:http-coroutines
 * @PackageName:org.coroutines.tool.http.response
 * @Author:null z
 * @DateTime:2017-09-03 19:30.
 */
@Data
@Builder
@ToString
@NoArgsConstructor
public class HttpResponse {

    @Getter
    @Setter
    private String result;

    @Getter
    @Setter
    private int statusCode;

//    /**
//     * 耗时
//     */
//    @Getter
//    @Setter
//    private long datetime;

    public HttpResponse(String result, int statusCode) {
        this.result = result;
        this.statusCode = statusCode;
    }
}
