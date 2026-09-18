package option

import (
	"context"
	"testing"

	"github.com/sagernet/sing/common/json"

	"github.com/stretchr/testify/require"
)

func TestWireGuardAmneziaRandomTrailersUnmarshal(t *testing.T) {
	t.Parallel()

	var endpoint WireGuardEndpointOptions
	err := json.UnmarshalContext(context.Background(), []byte(`{
		"address": ["10.0.0.2/32"],
		"private_key": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
		"amnezia": {
			"jc": 6,
			"random_trailers": true
		}
	}`), &endpoint)
	require.NoError(t, err)
	require.NotNil(t, endpoint.Amnezia)
	require.True(t, endpoint.Amnezia.RandomTrailers)
}
