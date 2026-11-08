package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class NativeMultiAreaTest {
    private List<PartTimeSearchService.Selection> selection() {
        var region = new PartTimeSearchService.Region("B070","031","구리시");
        return List.of(new PartTimeSearchService.Selection(region,new PartTimeNeighborhoods.Neighborhood("B0700130","인창동")),
                new PartTimeSearchService.Selection(region,new PartTimeNeighborhoods.Neighborhood("B0700100","수택동")));
    }
    @Test void eachSourceReceivesAllAreasInASingleRequestAndOwnUnionTotalIsKept() throws Exception {
        String mon;
        try (var input = getClass().getResourceAsStream("/part-time/mon-native-multi.html")) {
            mon = new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        String alba = "<input name='strAreaMulti' value='031||구리시||인창동,031||구리시||수택동'>"
                + "<div id='NormalInfo'>총 0건<table><tbody></tbody></table></div>";
        var urls = Collections.synchronizedList(new ArrayList<String>());
        var service = new PartTimeSearchService(new JobSearchRegionService(new ObjectMapper()),
                new PartTimeNeighborhoods(code -> "[{\"code\":\"B0700130\",\"name\":\"인창동\"},{\"code\":\"B0700100\",\"name\":\"수택동\"}]"),
                url -> { urls.add(URLDecoder.decode(url,StandardCharsets.UTF_8)); return url.contains("albamon.com") ? mon : alba; });
        var locations = List.of(new JobSearchRequest.Location("B000_B070","B0700130","인창동"),new JobSearchRequest.Location("B000_B070","B0700100","수택동"));
        var criteria = new JobSearchRequest("",List.of("B000_B070"),"ANY",null,null,"ANY",List.of(),null,null,locations);
        var response = service.search(new PartTimeSearchService.Request(criteria,1,1));
        assertEquals(2,urls.size(),"Two sources, not two requests per region");
        assertTrue(urls.stream().anyMatch(url -> url.contains("areas=B0700130,B0700100")));
        assertTrue(urls.stream().anyMatch(url -> url.contains("strAreaMulti=031||구리시||인창동,031||구리시||수택동")));
        assertTrue(urls.stream().anyMatch(url -> url.contains("&selDong=인창동&selDong=수택동")));
        assertTrue(urls.stream().allMatch(url -> url.contains("page=1")));
        assertTrue(response.sites().stream().allMatch(site -> site.status().equals("SUCCESS")),response.toString());
        assertEquals(258,response.sites().stream().filter(site -> site.source().equals("ALBAMON")).findFirst().orElseThrow().totalAvailable());
        assertThrows(IllegalStateException.class,() -> PartTimeSearchService.parseAlbaArea(alba.replace(",031||구리시||수택동",""),1,selection()));
        assertThrows(IllegalStateException.class,() -> PartTimeSearchService.parseMonArea(mon,1,List.of("B0700130","I0100050")));
        var keywordUrl = URLDecoder.decode(PartTimeSearchService.sourceUrl("ALBAMON","카페",selection(),2),StandardCharsets.UTF_8);
        assertTrue(keywordUrl.contains("keyword=카페"));
        assertTrue(keywordUrl.contains("areas=B0700130,B0700100"));
        assertTrue(keywordUrl.contains("page=2"));
    }
}
