-- Keep historical articles and revisions intact while removing these categories
-- from public pages and the publishing category selector.
UPDATE categories
SET enabled=FALSE, navigation=FALSE
WHERE id IN ('cooperation-standards','cooperation-alliance','cooperation-training')
   OR (parent_id='cooperation' AND name IN ('标委会活动','联盟活动','人才培训'));
