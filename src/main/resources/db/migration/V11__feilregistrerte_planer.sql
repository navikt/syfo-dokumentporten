UPDATE document
    SET delete_performed = now()
WHERE transmission_id IN ('019dfd2f-e0b1-7231-af89-3b6a67cc9d5c', '019e012e-909e-7060-a117-7e8d76b8de8b');
