import os
import uvicorn
from server import build_app

if __name__ == '__main__':
    app = build_app(workspace='/workspace',data='/data',port=8765,allow_commands=os.environ.get('CHIMERA_ALLOW_COMMANDS','false').lower()=='true')
    uvicorn.run(app,host='0.0.0.0',port=8765,log_level='info')
