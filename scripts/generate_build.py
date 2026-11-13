#!/usr/bin/env python3
# Основа для будущих генераторов построек. JSON-формат будет подключён на следующем этапе.
import json, sys
name=sys.argv[1] if len(sys.argv)>1 else 'new_build'
json.dump({'name':name,'ops':[]}, open(f'../builds/{name}.json','w'), ensure_ascii=False, indent=2)
