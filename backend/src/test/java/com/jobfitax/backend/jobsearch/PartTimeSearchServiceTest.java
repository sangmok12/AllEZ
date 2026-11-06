package com.jobfitax.backend.jobsearch;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

class PartTimeSearchServiceTest {
    @Test void albaParsingKeepsPayAndSeparatesCompanyFromArea() {
        String row = "<li data-imid='123'><span class='job-list__company'>가게<span class='job-list__area'>서울 마포구</span></span>"
            + "<span class='job-list__subject'>카페 모집</span><span class='payIcon'>시급</span>"
            + "<b class='job-list__number'>12,000</b><span class='job-list__deadline'>상시모집</span>"
            + "<div class='regDate'>2026.11.06<a>요약</a></div></li>";
        var r = PartTimeSearchService.parse("ALBAHEAVEN","<div>총 41건<div id='SearchJob'><ul id='jobNormal'>"+row+row+"</ul></div></div>",1);
        assertEquals(1,r.items().size());
        assertEquals("가게",r.items().getFirst().companyName());
        assertEquals(List.of("서울 마포구"),r.items().getFirst().locations());
        assertEquals("시급 12,000원",r.items().getFirst().payText());
        assertEquals("2026.11.06",r.items().getFirst().registeredDate());
        assertEquals(2,r.sites().getFirst().nextPage());
    }
    @Test void monParsingKeepsCompanyTitlePayAndDeduplicates() {
        String row="<li><span class='list-item-recruit__location'>서울 강남구</span><div class='list-item-recruit__contents--article'>"
            +"<a href='/jobs/detail/321'><div class='list-item-recruit__recruit-title'><span class='list-item-recruit__grey-text'>카페</span><span class='list-item-recruit__bar'></span><span>바리스타 모집</span></div></a>"
            +"<span>시급 13,000원 등록일 11/6 마감일 상시모집</span></div></li>";
        var r=PartTimeSearchService.parse("ALBAMON","<title>카페 공고 25건 | 알바몬</title><ul>"+row+row+"</ul>",1);
        assertEquals(1,r.items().size());
        assertEquals("바리스타 모집",r.items().getFirst().title());
        assertEquals("시급 13,000원",r.items().getFirst().payText());
        assertEquals(List.of("서울 강남구"),r.items().getFirst().locations());
    }
    @Test void rejectsChangedOrBlockedPageAndAcceptsGenuineZero() {
        assertThrows(IllegalStateException.class,()->PartTimeSearchService.parse("ALBAMON","<title>Access denied</title>",1));
        assertThrows(IllegalStateException.class,()->PartTimeSearchService.parse("ALBAHEAVEN","<div>총 10건<div id='SearchJob'></div></div>",1));
        var r=PartTimeSearchService.parse("ALBAHEAVEN","<div>총 0건<div id='SearchJob'><ul id='jobNormal'></ul></div></div>",1);
        assertNull(r.sites().getFirst().nextPage());
    }
    @Test void validatesRegionAndPage() {
        var service = new PartTimeSearchService(new JobSearchRegionService(new ObjectMapper()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            ()->service.search(new PartTimeSearchService.Request(criteria("카페", List.of("UNKNOWN")),1,1)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            ()->service.search(new PartTimeSearchService.Request(criteria("카페", List.of()),0,1)));
    }
    @Test
    @EnabledIfEnvironmentVariable(named="RENDER",matches="true")
    void renderDirectSearchReturnsBothSourcesAndAdditionalPages() {
        var service = new PartTimeSearchService(new JobSearchRegionService(new ObjectMapper()));
        var request = criteria("카페", List.of("I000"));
        var first = service.search(new PartTimeSearchService.Request(request,1,1));
        for(var site:first.sites()) {
            System.out.println("PART_TIME_INTEGRATION source="+site.source()+" status="+site.status()+" items="+site.returnedCount());
            assertEquals("SUCCESS",site.status(),site.source());
            assertTrue(site.returnedCount()>0,site.source());
            assertEquals(2,site.nextPage());
        }
        for(var item:first.items()) {
            assertFalse(item.title().isBlank());
            assertFalse(item.companyName().isBlank());
            assertFalse(item.payText().isBlank());
            assertTrue(item.url().startsWith("https://"));
        }
        var second=service.search(new PartTimeSearchService.Request(request,2,2));
        for(var site:second.sites()) assertEquals("SUCCESS",site.status(),site.source());
        for(String source:List.of("ALBAMON","ALBAHEAVEN")) {
            var ids=first.items().stream().filter(i->i.source().equals(source)).map(PartTimeSearchService.Item::postingId).toList();
            assertTrue(second.items().stream().filter(i->i.source().equals(source)).anyMatch(i->!ids.contains(i.postingId())));
        }
        var only=service.search(new PartTimeSearchService.Request(request,null,2));
        assertEquals(1,only.sites().size());
        assertEquals("ALBAHEAVEN",only.sites().getFirst().source());
    }
    private static JobSearchRequest criteria(String keyword,List<String> regions) {
        return new JobSearchRequest(keyword,regions,"ANY",null,null,"ANY",List.of());
    }
}
