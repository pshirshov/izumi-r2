
import { Logger } from '../../logger';
import { WebSocketClientTransport } from './transport.websocket.client';
import { ClientTransport, IncomingData, OutgoingData } from '../transport';
import { HTTPClientTransport } from './transport.http.client';
import { JSONMarshaller } from '../../marshaller';
import { AuthMethod } from '../auth';
import { TransportHeaders } from '../transport';

export class HybridClientTransportGeneric<C> implements ClientTransport {
    private _restTransport: HTTPClientTransport;
    private _wsTransport: WebSocketClientTransport<C>;
    private _authMethod: AuthMethod;
    private _headers: TransportHeaders;

    constructor(restEndpoint: string, wsEndpoint: string, marshaller: JSONMarshaller, logger: Logger) {
        this._headers = {};
        this._restTransport = new HTTPClientTransport(restEndpoint, marshaller, logger);
        this._wsTransport = new WebSocketClientTransport<C>(wsEndpoint, marshaller, logger);
    }

    public getAuthorization(): AuthMethod | undefined {
        return this._authMethod;
    }

    public setAuthorization(method: AuthMethod) {
        this._authMethod = method;
        this._restTransport.setAuthorization(method);
        this._wsTransport.setAuthorization(method);
    }

    public getHeaders(): TransportHeaders {
        return this._headers;
    }

    public setHeaders(headers: TransportHeaders) {
        this._headers = headers;
        this._restTransport.setHeaders(headers);
        this._wsTransport.setHeaders(headers);
    }

    public send(service: string, method: string, data: IncomingData): Promise<OutgoingData> {
        if (this._wsTransport.isReady()) {
            return this._wsTransport.send(service, method, data);
        }

        return this._restTransport.send(service, method, data);
    }
}

export class HybridClientTransport extends HybridClientTransportGeneric<object> {
}