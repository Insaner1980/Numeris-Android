DELETE FROM cloudflare_traffic;
DELETE FROM search_console;
WITH RECURSIVE days(d) AS (SELECT '2026-09-28' UNION ALL SELECT date(d,'+1 day') FROM days WHERE d<'2026-10-11')
INSERT INTO search_console(site_url,date,kind,query,clicks,impressions,position,fetched_at)
SELECT 'a.com',d,'daily','',10,100,2,'2026-10-11T12:00:00Z' FROM days;
WITH RECURSIVE days(d) AS (SELECT '2026-09-28' UNION ALL SELECT date(d,'+1 day') FROM days WHERE d<'2026-10-11')
INSERT INTO web_analytics_daily(domain,date,visits,page_views,fetched_at)
SELECT 'a.com',d,10,100,'2026-10-11T12:00:00Z' FROM days;
WITH RECURSIVE days(d) AS (SELECT '2026-09-28' UNION ALL SELECT date(d,'+1 day') FROM days WHERE d<'2026-10-11')
INSERT INTO web_analytics_pages(domain,date,path,page_views)
SELECT 'a.com',d,'/shared/',CASE WHEN d>='2026-10-05' THEN 20 ELSE 10 END FROM days;
INSERT INTO web_analytics_pages(domain,date,path,page_views) VALUES('b.com','2026-10-11','/shared/',50);
WITH RECURSIVE days(d) AS (SELECT '2026-09-28' UNION ALL SELECT date(d,'+1 day') FROM days WHERE d<'2026-10-11')
INSERT INTO search_console(site_url,date,kind,query,page,clicks,impressions,position,fetched_at)
SELECT 'a.com',d,'page','__page__','https://a.com/shared/',CASE WHEN d>='2026-10-05' THEN 10 ELSE 5 END,CASE WHEN d='2026-10-11' THEN 90 ELSE 10 END,CASE WHEN d='2026-10-11' THEN 9 ELSE 1 END,'2026-10-11T12:00:00Z' FROM days;
INSERT INTO search_console(site_url,date,kind,query,page,clicks,impressions,position,fetched_at)
VALUES('b.com','2026-10-11','page','__page__','https://b.com/Google%20only/',7,70,3,'2026-10-11T12:00:00Z');
INSERT INTO bing_page_stats(site_url,page_url,date,clicks,impressions,raw_json,fetched_at)
VALUES('https://a.com/','https://a.com/shared/','2026-10-11',3,30,'{}','2026-10-11T12:00:00Z');
