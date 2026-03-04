SET NAMES utf8mb4;

-- Hacker News 삭제
DELETE nt FROM news_tags nt
INNER JOIN news n ON nt.news_id = n.id
WHERE n.source = 'Hacker News';

DELETE FROM news WHERE source = 'Hacker News';
DELETE FROM news_stats WHERE source = 'Hacker News';

-- Velog 재분석 초기화
UPDATE news SET ai_result = NULL WHERE source = 'Velog';
