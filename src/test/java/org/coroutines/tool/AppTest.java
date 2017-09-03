package org.coroutines.tool;

import org.coroutines.tool.http.HttpInterface;
import org.coroutines.tool.http.response.HttpResponse;
import org.coroutines.tool.http.support.HttpClients;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {


    @Ignore
    @Test
    public void testHttp(){
        try {
            HttpInterface httpInterface = HttpClients.custom().build();
            HttpResponse httpResponse=httpInterface.synSendGet("https://www.baidu.com/");
            System.out.println(httpResponse.getResult());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
