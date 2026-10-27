-- Upgrade existing installations without renaming or replacing their categories.
-- Empty installations receive these categories from Bootstrap with the other roots.
INSERT INTO categories (id,name,slug,parent_id,type,template,sort_order,enabled,navigation)
SELECT 'cooperation-conferences','学术会议','cooperation-conferences','cooperation','ARTICLE','list',91,TRUE,TRUE
WHERE EXISTS (SELECT 1 FROM categories WHERE id='cooperation')
AND NOT EXISTS (SELECT 1 FROM categories WHERE id='cooperation-conferences' OR slug='cooperation-conferences' OR (parent_id='cooperation' AND name='学术会议'));
INSERT INTO categories (id,name,slug,parent_id,type,template,sort_order,enabled,navigation)
SELECT 'cooperation-journals','学术期刊','cooperation-journals','cooperation','ARTICLE','list',92,TRUE,TRUE
WHERE EXISTS (SELECT 1 FROM categories WHERE id='cooperation')
AND NOT EXISTS (SELECT 1 FROM categories WHERE id='cooperation-journals' OR slug='cooperation-journals' OR (parent_id='cooperation' AND name='学术期刊'));
INSERT INTO categories (id,name,slug,parent_id,type,template,sort_order,enabled,navigation)
SELECT 'cooperation-international','国际交流','cooperation-international','cooperation','ARTICLE','list',93,TRUE,TRUE
WHERE EXISTS (SELECT 1 FROM categories WHERE id='cooperation')
AND NOT EXISTS (SELECT 1 FROM categories WHERE id='cooperation-international' OR slug='cooperation-international' OR (parent_id='cooperation' AND name='国际交流'));
INSERT INTO categories (id,name,slug,parent_id,type,template,sort_order,enabled,navigation)
SELECT 'cooperation-standards','标委会活动','cooperation-standards','cooperation','ARTICLE','list',94,TRUE,TRUE
WHERE EXISTS (SELECT 1 FROM categories WHERE id='cooperation')
AND NOT EXISTS (SELECT 1 FROM categories WHERE id='cooperation-standards' OR slug='cooperation-standards' OR (parent_id='cooperation' AND name='标委会活动'));
INSERT INTO categories (id,name,slug,parent_id,type,template,sort_order,enabled,navigation)
SELECT 'cooperation-alliance','联盟活动','cooperation-alliance','cooperation','ARTICLE','list',95,TRUE,TRUE
WHERE EXISTS (SELECT 1 FROM categories WHERE id='cooperation')
AND NOT EXISTS (SELECT 1 FROM categories WHERE id='cooperation-alliance' OR slug='cooperation-alliance' OR (parent_id='cooperation' AND name='联盟活动'));
INSERT INTO categories (id,name,slug,parent_id,type,template,sort_order,enabled,navigation)
SELECT 'cooperation-training','人才培训','cooperation-training','cooperation','ARTICLE','list',96,TRUE,TRUE
WHERE EXISTS (SELECT 1 FROM categories WHERE id='cooperation')
AND NOT EXISTS (SELECT 1 FROM categories WHERE id='cooperation-training' OR slug='cooperation-training' OR (parent_id='cooperation' AND name='人才培训'));
INSERT INTO categories (id,name,slug,parent_id,type,template,sort_order,enabled,navigation)
SELECT 'cooperation-enterprise','企业合作','cooperation-enterprise','cooperation','ARTICLE','list',97,TRUE,TRUE
WHERE EXISTS (SELECT 1 FROM categories WHERE id='cooperation')
AND NOT EXISTS (SELECT 1 FROM categories WHERE id='cooperation-enterprise' OR slug='cooperation-enterprise' OR (parent_id='cooperation' AND name='企业合作'));
