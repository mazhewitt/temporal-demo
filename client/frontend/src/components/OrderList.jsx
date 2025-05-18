import { useState, useEffect } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Link,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  CircularProgress
} from '@mui/material';
import { OrderService } from '../services/OrderService';

const getStatusColor = (status) => {
  switch (status) {
    case 'COMPLETED':
      return 'success';
    case 'REJECTED':
    case 'EXPIRED':
      return 'error';
    case 'IN_PROGRESS':
      return 'primary';
    default:
      return 'default';
  }
};

const OrderList = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  const fetchOrders = async () => {
    try {
      setLoading(true);
      const data = await OrderService.getAllOrders();
      setOrders(data);
      setError(null);
    } catch (err) {
      console.error('Error fetching orders:', err);
      setError('Failed to load orders. Please try again.');
    } finally {
      setLoading(false);
    }
  };
  
  useEffect(() => {
    fetchOrders();
    
    // Set up polling for updates
    const intervalId = setInterval(fetchOrders, 10000); // Poll every 10 seconds
    
    return () => clearInterval(intervalId); // Clean up on unmount
  }, []);
  
  const handleRefresh = () => {
    fetchOrders();
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Orders</Typography>
        <Button variant="outlined" onClick={handleRefresh} disabled={loading}>
          Refresh
        </Button>
      </Box>
      
      {loading && !orders.length ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 3 }}>
          <CircularProgress />
        </Box>
      ) : error ? (
        <Typography color="error">{error}</Typography>
      ) : orders.length === 0 ? (
        <Card>
          <CardContent>
            <Typography>No orders found. Create a new order to get started.</Typography>
            <Button 
              component={RouterLink} 
              to="/orders/new"
              variant="contained" 
              sx={{ mt: 2 }}
            >
              Create Order
            </Button>
          </CardContent>
        </Card>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Order ID</TableCell>
                <TableCell>Product Type</TableCell>
                <TableCell>Client</TableCell>
                <TableCell>Quote Price</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.map((order) => (
                <TableRow key={order.orderId}>
                  <TableCell>{order.orderId.substring(0, 8)}...</TableCell>
                  <TableCell>
                    {/* We don't directly have product type in the response, this would need to be added */}
                    {order.productType || "N/A"}
                  </TableCell>
                  <TableCell>
                    {/* We don't directly have client in the response, this would need to be added */}
                    {order.client || "N/A"}
                  </TableCell>
                  <TableCell>
                    {order.quote ? `$${order.quote.price.toFixed(2)}` : 'N/A'}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={order.status}
                      color={getStatusColor(order.status)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>
                    <Link component={RouterLink} to={`/orders/${order.orderId}`}>
                      View Details
                    </Link>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
};

export default OrderList;
